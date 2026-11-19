package com.dsinnovators.ei.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** The compiled production classes of the backend, imported once for all architecture tests. */
final class ProductionClasses {

    static final String BASE_PACKAGE = "com.dsinnovators.ei";

    static final JavaClasses ALL = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    private ProductionClasses() {}
}
