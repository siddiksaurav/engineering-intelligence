package com.dsinnovators.ei;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EiApplicationJobModeTest {

    @ParameterizedTest
    @ValueSource(strings = {"--job=x", "--job=", "--job"})
    void anArgumentThatNamesAJobSwitchesToJobMode(String argument) {
        assertTrue(EiApplication.isJobMode(new String[] {argument}));
    }

    @Test
    void theJobArgumentMayComeAfterOtherArguments() {
        assertTrue(EiApplication.isJobMode(new String[] {"--a=b", "--job=x"}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--server.port=9000", "--jobs=x", "job=x"})
    void anyOtherArgumentLeavesTheApplicationAWebServer(String argument) {
        assertFalse(EiApplication.isJobMode(new String[] {argument}));
    }

    @Test
    void withoutArgumentsTheApplicationIsTheWebServer() {
        assertFalse(EiApplication.isJobMode(new String[] {}));
    }
}
