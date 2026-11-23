package dev.rafaeltoledo.ledger.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.rafaeltoledo.ledger", importOptions = ImportOption.DoNotIncludeTests.class)
class AdapterArchitectureTest {

    @ArchTest
    static final ArchRule adapters_are_independent_of_each_other = slices()
            .matching("dev.rafaeltoledo.ledger.adapter.(*)..")
            .should().notDependOnEachOther()
            .because("adapters only meet in the application ports");

    @ArchTest
    static final ArchRule adapters_do_not_reach_into_configuration = noClasses()
            .that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..config..");
}
