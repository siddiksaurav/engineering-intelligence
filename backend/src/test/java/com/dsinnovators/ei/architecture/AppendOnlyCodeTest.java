package com.dsinnovators.ei.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import jakarta.persistence.Entity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

/**
 * {@code raw_record} is never edited; {@code evidence_item} and {@code judgment} are append-only.
 * These rules check that the code offers no way to change or remove a row of those tables. The
 * database side is checked by {@link AppendOnlyTablesTest}.
 */
class AppendOnlyCodeTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main");

    @Test
    void theRawRecordEntityIsImmutable() {
        entityIsImmutable("RawRecord");
    }

    @Test
    void theEvidenceItemEntityIsImmutable() {
        entityIsImmutable("EvidenceItem");
    }

    @Test
    void theJudgmentEntityIsImmutable() {
        entityIsImmutable("Judgment");
    }

    @Test
    void noRepositoryOfRawRecordCanUpdateOrDelete() {
        repositoryCannotUpdateOrDelete("RawRecord");
    }

    @Test
    void noRepositoryOfEvidenceItemCanUpdateOrDelete() {
        repositoryCannotUpdateOrDelete("EvidenceItem");
    }

    @Test
    void noRepositoryOfJudgmentCanUpdateOrDelete() {
        repositoryCannotUpdateOrDelete("Judgment");
    }

    // Covers what the repository rules cannot see: SQL in JdbcTemplate calls, native queries and
    // migrations. A table name that does not exist yet costs nothing to guard from the start.
    @ParameterizedTest
    @ValueSource(strings = {"raw_record", "evidence_item", "judgment"})
    void noProductionCodeOrMigrationUpdatesOrDeletesRowsOf(String table) throws IOException {
        Pattern changingStatement = Pattern.compile(
                "\\b(update\\s+" + table + "\\b|delete\\s+from\\s+" + table + "\\b|truncate\\s+(table\\s+)?" + table
                        + "\\b)",
                Pattern.CASE_INSENSITIVE);

        assertEquals(List.of(), filesMatching(changingStatement));
    }

    private static void entityIsImmutable(String entityName) {
        classes()
                .that()
                .areAnnotatedWith(Entity.class)
                .and()
                .haveSimpleName(entityName)
                .should()
                .beAnnotatedWith(Immutable.class)
                .check(ProductionClasses.ALL);
    }

    private static void repositoryCannotUpdateOrDelete(String entityName) {
        DescribedPredicate<JavaClass> repositoryOfEntity = repositoryOf(entityName);

        noClasses()
                .that(repositoryOfEntity)
                .should()
                .beAssignableTo(CrudRepository.class)
                .because("CrudRepository brings delete methods and a save that updates an existing row")
                .check(ProductionClasses.ALL);
        noMethods()
                .that()
                .areDeclaredInClassesThat(repositoryOfEntity)
                .should()
                .haveNameMatching("(delete|remove|update).*")
                .orShould()
                .beAnnotatedWith(Modifying.class)
                .check(ProductionClasses.ALL);
    }

    private static DescribedPredicate<JavaClass> repositoryOf(String entityName) {
        return DescribedPredicate.describe(
                "are Spring Data repositories of " + entityName,
                javaClass -> javaClass.isAssignableTo(Repository.class)
                        && javaClass.getInterfaces().stream().anyMatch(type -> firstTypeArgumentIs(type, entityName)));
    }

    private static boolean firstTypeArgumentIs(JavaType type, String entityName) {
        return type instanceof JavaParameterizedType parameterized
                && !parameterized.getActualTypeArguments().isEmpty()
                && parameterized
                        .getActualTypeArguments()
                        .getFirst()
                        .toErasure()
                        .getSimpleName()
                        .equals(entityName);
    }

    private static List<String> filesMatching(Pattern pattern) throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(Files::isRegularFile)
                    .filter(file ->
                            file.toString().endsWith(".java") || file.toString().endsWith(".sql"))
                    .filter(file -> pattern.matcher(read(file)).find())
                    .map(Path::toString)
                    .toList();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the source file " + file, e);
        }
    }
}
