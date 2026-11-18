package com.promptlib;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.promptlib", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_never_sees_infrastructure =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.promptlib.persistence..", "com.promptlib.grpc..", "com.promptlib.adapter..",
                            "jakarta.ws.rs..", "io.grpc..", "com.promptlib.proto..");

    @ArchTest
    static final ArchRule shared_is_a_leaf =
            noClasses().that().resideInAPackage("..shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.promptlib.domain..", "com.promptlib.persistence..",
                            "com.promptlib.grpc..", "com.promptlib.adapter..");

    @ArchTest
    static final ArchRule grpc_does_not_use_the_entity_manager =
            noClasses().that().resideInAPackage("..grpc..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..", "io.quarkus.hibernate.orm.panache..");

    @ArchTest
    static final ArchRule adapters_do_not_leak_into_services =
            classes().that().resideInAPackage("..domain..service..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "java..", "jakarta..", "org.jboss.logging..",
                            "com.promptlib.domain..", "com.promptlib.shared..");
}
