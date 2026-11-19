package com.dsinnovators.ei.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * The model is reached only through the {@code JudgmentModel} interface, and only from the {@code
 * judgment} package.
 */
class JudgmentModelBoundaryTest {

    private static final String JUDGMENT_PACKAGE = ProductionClasses.BASE_PACKAGE + ".judgment..";

    // The client libraries of the LLM providers the PoC may use (decision 7). A new provider's
    // package is added here when its library is added to the build.
    private static final String[] MODEL_CLIENT_PACKAGES = {
        "com.anthropic..", "org.springframework.ai..", "com.openai..", "com.google.genai.."
    };

    @Test
    void noClassOutsideTheJudgmentPackageUsesAModelClient() {
        noClasses()
                .that()
                .resideOutsideOfPackage(JUDGMENT_PACKAGE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(MODEL_CLIENT_PACKAGES)
                .check(ProductionClasses.ALL);
    }

    @Test
    @Disabled("F06 creates JudgmentModel; enable this test in F06")
    void noClassOutsideTheJudgmentPackageUsesJudgmentModel() {
        noClasses()
                .that()
                .resideOutsideOfPackage(JUDGMENT_PACKAGE)
                .should()
                .dependOnClassesThat()
                .haveSimpleName("JudgmentModel")
                .check(ProductionClasses.ALL);
    }

    @Test
    @Disabled("F06 creates JudgmentModel and its first implementation; enable this test in F06")
    void insideTheJudgmentPackageOnlyAJudgmentModelUsesAModelClient() {
        noClasses()
                .that()
                .resideInAPackage(JUDGMENT_PACKAGE)
                .and()
                .doNotImplement(simpleName("JudgmentModel"))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(MODEL_CLIENT_PACKAGES)
                .check(ProductionClasses.ALL);
    }
}
