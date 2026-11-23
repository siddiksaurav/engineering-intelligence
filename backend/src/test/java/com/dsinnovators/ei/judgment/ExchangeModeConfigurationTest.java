package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** In exchange mode the model is the exchange alone, and no provider key is needed. */
@TestPropertySource(properties = "ei.judgment.mode=exchange")
class ExchangeModeConfigurationTest extends DatabaseTest {

    @Autowired
    private ProviderChain chain;

    @Test
    void theOnlyProviderIsTheExchange() {
        assertEquals(List.of("claude-code"), chain.providerNames());
    }
}
