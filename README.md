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
docker compose up -d          # Postgres + Redpanda + Keycloak
./gradlew bootRun
```

Swagger UI at `http://localhost:8083/swagger-ui.html`. Watch the console for `[MOCK EMAIL]` /
`[MOCK SMS]` lines as the outbox dispatcher and consumer drain a request end to end.

```bash
./gradlew test
```
