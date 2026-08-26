# notification-service

Part of [`card-billing-modernization`](https://github.com/leon-lourenco/card-billing-modernization) —
the modernized counterpart to [`card-billing-legacy`](https://github.com/leon-lourenco/card-billing-legacy).
Full architecture, contracts, and cross-cutting decisions live in that repo's
[`ARCHITECTURE.md`](https://github.com/leon-lourenco/card-billing-modernization/blob/master/ARCHITECTURE.md) —
this README covers what's specific to this one service.

Owns notification dispatch — this time with an actual delivery guarantee. The legacy's
`NotificationRequestPublisher` wrote a database row, then published to Kafka separately with
nothing tying the two together; a real live run left 12 of 42 notification requests permanently
stuck because of exactly that gap. This service closes it with a real outbox.

## API

| Endpoint | Purpose | Idempotency |
|---|---|---|
| `POST /notifications` `{customerId, invoiceId, channel, stage}` | Requests a notification | Unique on `(invoiceId, stage)` — a duplicate request returns the existing record instead of creating a second one |

## The fix, concretely

`POST /notifications` writes the `Notification` row *and* an `OutboxEvent` row in one local
database transaction, then returns `202` — the request is durable the instant this call returns,
regardless of whether Kafka is reachable at that moment. A separate `OutboxDispatcher` (same
pattern as [`pix-payment-gateway`](https://github.com/leon-lourenco/pix-payment-gateway)'s,
already proven there) polls the outbox table and publishes to the `notification.requested` Kafka
topic. `NotificationDeliveryConsumer` — in this same service, consuming its own topic —
simulates delivery (mocked, logged; no real Twilio/SendGrid account, so no cost) and marks the
notification `SENT`.

The database write and the "this will eventually publish" guarantee are now atomic. That's the
whole fix — the legacy's gap was never about Kafka being unreliable, it was about a write with no
guarantee tied to it.

Errors are `application/problem+json` via this service's own domain exceptions.
`DuplicateNotificationException` is caught and turned into the existing record per the
idempotency rule above, not surfaced as a failure — it's here for observability, not to reject a
legitimate retry.

## Engineering practices

Hexagonal package structure (`domain` / `application` / `infrastructure`), enforced by ArchUnit
in every test run — see `ARCHITECTURE.md` in the hub repo for the exact rules. Tests written
alongside implementation, not after.

## Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.1.0 |
| Build | Gradle (Kotlin DSL) | 9.7.1 |
| API docs | springdoc-openapi-starter-webmvc-ui | 3.1.0 |
| Messaging | Redpanda (Kafka-API-compatible) | latest |
| Auth | Keycloak (resource server) | 26.7 |
| Database | PostgreSQL | 16 |
| Architecture tests | ArchUnit | — |

## Running it

```bash
docker compose up -d          # Postgres (5435) + Redpanda (9092) + Keycloak (8090)
./gradlew bootRun
```

Every endpoint then needs a Bearer token from the `card-billing` realm:

```bash
TOKEN=$(curl -s -X POST http://localhost:8090/realms/card-billing/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=collections-service \
  -d client_secret=collections-service-secret | jq -r .access_token)

curl -X POST http://localhost:8083/notifications \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId":42,"invoiceId":108,"channel":"EMAIL","stage":"REMINDER_D5","recipient":"maria.silva@example.com"}'
```

Keycloak is much heavier than the other two containers, and on a constrained machine it can spend
several minutes starting before it answers anything. Since it has nothing to do with the outbox,
there is an opt-in profile that drops the token requirement so the loop can be exercised with two
containers and `curl` alone:

```bash
docker compose up -d postgres redpanda
./gradlew bootRun --args='--spring.profiles.active=local'
```

It is never the default — the resource server is what runs otherwise, and the integration tests
still assert that an unauthenticated request is rejected.

Swagger UI at `http://localhost:8083/swagger-ui.html`. Watch the console for `[MOCK EMAIL]` /
`[MOCK SMS]` lines as the outbox dispatcher and consumer drain a request end to end.

```bash
./gradlew test
```

The integration tests run real Postgres and real Redpanda via Testcontainers, so Docker has to be
running. `NotificationOutboxLoopIT` is the one that matters: it drives the whole chain and asserts
the result in raw SQL rather than through the application's own repositories.

## Verified

A real run against `docker compose up` — Postgres 16 and Redpanda, three requests posted with
`curl`:

```
10:43:47.477  RequestNotificationUseCase  Accepted notification 5f9e373e… for invoice 108 at stage
                                          REMINDER_D5 - outbox event written in the same transaction
10:43:53.241  OutboxDispatcher            Outbox dispatch pass failed - events stay pending and will
                                          be retried: Failed to publish outbox event 627a9958… to Kafka
10:43:55.375  MockNotificationSender      [MOCK EMAIL] To maria.silva@example.com (customer 42):
                                          invoice #108 is at stage REMINDER_D5
10:43:55.476  PublishPendingOutboxEvents  Published 1 outbox event(s)
10:43:56.500  DispatchNotificationUseCase Notification 5f9e373e… was already sent at 13:43:55.375685Z
                                          - skipping redelivery
```

That middle line is the whole point, and it was not staged: the first publish attempt hit the five
second acknowledgement deadline while the topic was still being created. Under the monolith's
publisher that request was gone — the row said `REQUESTED` and nothing would ever look at it again.
Here the transaction rolled back, the event stayed pending, the next poll republished it, and the
duplicate that produced was absorbed by the consumer's already-sent guard.

Final state, after three POSTs of which one was a deliberate repeat:

```
 invoice_id | channel |       stage       | status | delivered | published
------------+---------+-------------------+--------+-----------+-----------
        108 | EMAIL   | REMINDER_D5       | SENT   | t         | t
        108 | SMS     | FORMAL_NOTICE_D30 | SENT   | t         | t

 notifications | outbox_events | still_pending
---------------+---------------+---------------
             2 |             2 |             0
```

Two rows, not three: the repeated request returned `200` with the existing record and wrote nothing.
