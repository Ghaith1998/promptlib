package com.promptlib.edge;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** The edge may only know proto types; the domain and persistence stay inside core. */
@AnalyzeClasses(packages = "com.promptlib.edge", importOptions = ImportOption.DoNotIncludeTests.class)
class EdgeArchitectureTest {

    @ArchTest
    static final ArchRule edge_has_no_domain_access =
            noClasses().that().resideInAPackage("com.promptlib.edge..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.promptlib.domain..", "com.promptlib.persistence..");
}
