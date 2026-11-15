package io.github.jerojasol.flights.infrastructure;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

@AnalyzeClasses(packages = "io.github.jerojasol.flights", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "io.github.jerojasol.flights.domain..";
    private static final String APPLICATION = "io.github.jerojasol.flights.application..";
    private static final String INFRASTRUCTURE = "io.github.jerojasol.flights.infrastructure..";
    private static final String CONFIG = "..infrastructure.config..";

    private static final DescribedPredicate<JavaAnnotation<?>> FRAMEWORK_ANNOTATION = DescribedPredicate.describe(
            "a framework annotation (org.springframework, jakarta, tools.jackson)",
            annotation -> {
                String pkg = annotation.getRawType().getPackageName();
                return pkg.startsWith("org.springframework") || pkg.startsWith("jakarta") || pkg.startsWith("tools.jackson");
            });

    @ArchTest
    static final ArchRule domain_depends_only_on_jdk_and_itself = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN);

    @ArchTest
    static final ArchRule application_depends_only_on_jdk_and_domain = classes()
            .that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN, APPLICATION);

    @ArchTest
    static final ArchRule domain_has_no_framework_annotations = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().beAnnotatedWith(FRAMEWORK_ANNOTATION);

    @ArchTest
    static final ArchRule web_does_not_touch_outbound_adapters = noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure.persistence..", "..infrastructure.provider..", "..infrastructure.cache..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule model_does_not_depend_on_service_search_or_aggregation = noClasses()
            .that().resideInAPackage("io.github.jerojasol.flights.domain.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.github.jerojasol.flights.domain.service..",
                    "io.github.jerojasol.flights.domain.search..",
                    "io.github.jerojasol.flights.domain.aggregation..");

    @ArchTest
    static final ArchRule aggregation_does_not_depend_on_search_or_service = noClasses()
            .that().resideInAPackage("io.github.jerojasol.flights.domain.aggregation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.github.jerojasol.flights.domain.search..",
                    "io.github.jerojasol.flights.domain.service..");

    @ArchTest
    static final ArchRule controllers_depend_only_on_inbound_ports_and_web = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "..domain.port.in..", "..domain.model..", "..domain.search..", "..domain.aggregation..",
                    "..infrastructure.web..",
                    "org.springframework..", "jakarta.validation..", "java..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule jpa_entities_stay_in_persistence = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("..infrastructure.persistence..")
            .andShould().onlyBeAccessed().byAnyPackage("..infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule provider_dtos_stay_in_their_adapter = CompositeArchRule
            .of(providerStaysInItsAdapter("alpha"))
            .and(providerStaysInItsAdapter("beta"))
            .and(providerStaysInItsAdapter("gamma"));

    @ArchTest
    static final ArchRule ports_are_interfaces = classes()
            .that().resideInAPackage("..domain.port..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule no_field_injection = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");

    @ArchTest
    static final ArchRule layers_point_inwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Infrastructure").definedBy(INFRASTRUCTURE)
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    private static ArchRule providerStaysInItsAdapter(String provider) {
        String adapter = "..infrastructure.provider." + provider + "..";
        return classes()
                .that().resideInAPackage(adapter)
                .should().onlyBeAccessed().byAnyPackage(adapter, CONFIG)
                .allowEmptyShould(true);
    }
}
