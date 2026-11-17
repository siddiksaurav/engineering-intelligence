package com.dsinnovators.ei;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EiApplicationJobModeTest {

    @Test
    void anArgumentThatNamesAJobSwitchesToJobMode() {
        assertTrue(EiApplication.isJobMode(new String[] {"--job=x"}));
        assertTrue(EiApplication.isJobMode(new String[] {"--a=b", "--job=x"}));
        assertTrue(EiApplication.isJobMode(new String[] {"--job="}));
        assertTrue(EiApplication.isJobMode(new String[] {"--job"}));
    }

    @Test
    void withoutAJobArgumentTheApplicationIsTheWebServer() {
        assertFalse(EiApplication.isJobMode(new String[] {}));
        assertFalse(EiApplication.isJobMode(new String[] {"--server.port=9000"}));
        assertFalse(EiApplication.isJobMode(new String[] {"--jobs=x"}));
    }
}
