package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The tests run without a provider key, so the application must start and have no provider. */
class JudgmentConfigurationTest extends DatabaseTest {

    @Autowired
    private ProviderChain chain;

    @Test
    void theApplicationStartsWithoutAnyProviderKey() {
        assertEquals(List.of(), chain.providerNames());
    }
}
