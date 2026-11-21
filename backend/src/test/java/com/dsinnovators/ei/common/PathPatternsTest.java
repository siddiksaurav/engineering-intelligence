package com.dsinnovators.ei.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PathPatternsTest {

    @Test
    void aPatternEndingInASlashIsADirectoryFromTheRepositoryRoot() {
        PathPatterns patterns = new PathPatterns(List.of("vendor/"));

        assertTrue(patterns.matches("vendor/a/b.rb"));
        assertFalse(patterns.matches("app/vendor/b.rb"));
        assertFalse(patterns.matches("vendor.rb"));
    }

    @Test
    void aStarStaysInsideOneFolderName() {
        PathPatterns patterns = new PathPatterns(List.of("modules/*/spec/"));

        assertTrue(patterns.matches("modules/meeting/spec/a_spec.rb"));
        assertFalse(patterns.matches("modules/spec/a_spec.rb"));
        assertFalse(patterns.matches("modules/a/b/spec/a_spec.rb"));
    }

    @Test
    void aPatternWithoutASlashMatchesTheFileNameAtAnyDepth() {
        PathPatterns patterns = new PathPatterns(List.of("*.lock", "Gemfile.lock"));

        assertTrue(patterns.matches("yarn.lock"));
        assertTrue(patterns.matches("a/b/yarn.lock"));
        assertTrue(patterns.matches("modules/x/Gemfile.lock"));
        assertFalse(patterns.matches("a.lock/readme.txt"));
    }

    @Test
    void aPatternWithASlashInsideMatchesTheWholePath() {
        PathPatterns patterns = new PathPatterns(List.of("db/structure.sql"));

        assertTrue(patterns.matches("db/structure.sql"));
        assertFalse(patterns.matches("x/db/structure.sql"));
    }

    @Test
    void regexCharactersInAPatternAreLiteral() {
        PathPatterns patterns = new PathPatterns(List.of("a+b(1).txt"));

        assertTrue(patterns.matches("a+b(1).txt"));
        assertFalse(patterns.matches("aab1.txt"));
    }

    @Test
    void noPatternMatchesNothing() {
        assertFalse(new PathPatterns(List.of()).matches("a.rb"));
    }
}
