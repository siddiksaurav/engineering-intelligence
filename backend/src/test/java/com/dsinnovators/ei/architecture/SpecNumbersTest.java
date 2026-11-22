package com.dsinnovators.ei.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * No number from the spec is hard-coded: thresholds, weights and done statuses are rows in {@code
 * setting}, read through {@code SettingService}. The packages that apply the spec's rules may hold no
 * number constant, neither a static final number nor a number on an enum constant. A number written
 * inline in a method cannot be seen in the compiled class; the reviewer checks for those.
 *
 * <p>Operating numbers of connectors (retries, page sizes) are configuration properties (decision 45)
 * and live outside these packages.
 */
class SpecNumbersTest {

    private static final Set<String> NUMBER_TYPES = Set.of(
            "byte",
            "short",
            "int",
            "long",
            "float",
            "double",
            Byte.class.getName(),
            Short.class.getName(),
            Integer.class.getName(),
            Long.class.getName(),
            Float.class.getName(),
            Double.class.getName(),
            BigDecimal.class.getName(),
            BigInteger.class.getName());

    private static final DescribedPredicate<JavaField> NUMBER_CONSTANT = DescribedPredicate.describe(
            "a number constant",
            field -> NUMBER_TYPES.contains(field.getRawType().getName())
                    && !field.getName().equals("serialVersionUID")
                    && (isStaticFinal(field) || field.getOwner().isEnum()));

    @Test
    void theEvidencePackageHoldsNoNumberConstant() {
        holdsNoNumberConstant("evidence");
    }

    @Test
    void theJudgmentPackageHoldsNoNumberConstant() {
        holdsNoNumberConstant("judgment");
    }

    @Test
    void theAnalyticsPackageHoldsNoNumberConstant() {
        holdsNoNumberConstant("analytics");
    }

    @Test
    @Disabled("F11 creates the scoring package; enable this test in F11")
    void theScoringPackageHoldsNoNumberConstant() {
        holdsNoNumberConstant("scoring");
    }

    private static void holdsNoNumberConstant(String packageName) {
        noFields()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(ProductionClasses.BASE_PACKAGE + "." + packageName + "..")
                .should(ArchCondition.from(NUMBER_CONSTANT))
                .because("numbers from the spec are rows in setting (spec 14)")
                .check(ProductionClasses.ALL);
    }

    private static boolean isStaticFinal(JavaField field) {
        return field.getModifiers().containsAll(Set.of(JavaModifier.STATIC, JavaModifier.FINAL));
    }
}
