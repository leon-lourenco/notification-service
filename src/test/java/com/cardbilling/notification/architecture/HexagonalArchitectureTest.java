package com.cardbilling.notification.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The hexagonal structure, checked rather than claimed. A broken rule here fails the build exactly
 * like a broken test, which is the only thing that keeps "we follow hexagonal architecture" from
 * decaying into a sentence in a README.
 */
@AnalyzeClasses(
        packages = "com.cardbilling.notification",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_does_not_depend_on_the_layers_around_it =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..infrastructure..")
                    .because("the domain is the centre of the hexagon - everything points inwards at it");

    @ArchTest
    static final ArchRule domain_is_plain_java =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.validation..",
                            "org.hibernate..",
                            "org.apache.kafka..",
                            "io.swagger..")
                    .because(
                            "a domain model annotated for persistence or serialisation is shaped by those"
                                    + " frameworks, not by the business - the JPA mapping lives in"
                                    + " infrastructure.persistence instead");

    @ArchTest
    static final ArchRule application_reaches_infrastructure_only_through_its_own_ports =
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .because(
                            "use cases depend on ports they declare; the adapters that implement them are"
                                    + " swappable details");

    @ArchTest
    static final ArchRule controllers_do_not_reach_into_persistence =
            noClasses()
                    .that()
                    .resideInAPackage("..infrastructure.web..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..")
                    .because(
                            "a controller that queries a repository directly puts business rules in the"
                                    + " transport layer - it calls into application instead");

    @ArchTest
    static final ArchRule messaging_reaches_persistence_only_through_the_application_layer =
            noClasses()
                    .that()
                    .resideInAPackage("..infrastructure.messaging..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..")
                    .because(
                            "the outbox dispatcher and the delivery consumer are adapters like any other -"
                                    + " they drive use cases rather than the database");

    @ArchTest
    static final ArchRule classes_named_entity_are_jpa_entities =
            classes()
                    .that()
                    .haveSimpleNameEndingWith("Entity")
                    .and()
                    .resideOutsideOfPackage("..domain..")
                    .should()
                    .beAnnotatedWith(jakarta.persistence.Entity.class)
                    .because("the name promises a persistence mapping, so drift from one is caught here");

    @ArchTest
    static final ArchRule jpa_entities_are_named_entity =
            classes()
                    .that()
                    .areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should()
                    .haveSimpleNameEndingWith("Entity")
                    .andShould()
                    .resideInAPackage("..infrastructure.persistence..")
                    .because(
                            "the same drift in the other direction: a persistent class that does not look"
                                    + " like one, or one that escaped the persistence package");
}
