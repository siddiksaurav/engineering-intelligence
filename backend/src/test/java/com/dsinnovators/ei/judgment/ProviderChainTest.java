package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.common.CanonicalJson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderChainTest {

    private static final ModelRequest REQUEST =
            new ModelRequest("v1", "Instructions", "Input", "schema", CanonicalJson.parse("{\"type\":\"object\"}"));

    private final List<String> asked = new ArrayList<>();

    private JudgmentModel answering(String name, String model) {
        return request -> {
            asked.add(name);
            return new ModelAnswer(CanonicalJson.parse("{}"), model, 10, 5);
        };
    }

    private JudgmentModel atItsLimit(String name) {
        return request -> {
            asked.add(name);
            throw new ProviderLimitReachedException(name, "daily tokens used");
        };
    }

    private static ProviderChain chainOf(ProviderChain.Provider... providers) {
        return new ProviderChain(List.of(providers), new WaitMeter(duration -> {}));
    }

    @Test
    void movesToTheNextProviderWhenOneReachesItsLimit() {
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", atItsLimit("groq")),
                new ProviderChain.Provider("gemini", answering("gemini", "gemini-2.5-flash")));

        ModelAnswer answer = chain.judge(REQUEST);

        assertEquals("gemini-2.5-flash", answer.model());
        assertEquals(List.of("groq", "gemini"), asked);
    }

    @Test
    void asksTheFirstProviderFirstAndOnlyIt() {
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", answering("groq", "gpt-oss")),
                new ProviderChain.Provider("gemini", answering("gemini", "flash")));

        chain.judge(REQUEST);

        assertEquals(List.of("groq"), asked);
    }

    @Test
    void doesNotAskAProviderAtItsLimitAgainInTheSameRun() {
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", atItsLimit("groq")),
                new ProviderChain.Provider("gemini", answering("gemini", "flash")));

        chain.judge(REQUEST);
        chain.judge(REQUEST);

        assertEquals(List.of("groq", "gemini", "gemini"), asked);
    }

    @Test
    void asksAProviderAgainAfterANewRunStarts() {
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", atItsLimit("groq")),
                new ProviderChain.Provider("gemini", answering("gemini", "flash")));
        chain.judge(REQUEST);

        chain.startRun();
        chain.judge(REQUEST);

        assertEquals(List.of("groq", "gemini", "groq", "gemini"), asked);
    }

    @Test
    void failsWithTheLimitReachedWhenNoProviderIsLeft() {
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", atItsLimit("groq")),
                new ProviderChain.Provider("gemini", atItsLimit("gemini")));

        ModelLimitReachedException thrown = assertThrows(ModelLimitReachedException.class, () -> chain.judge(REQUEST));

        assertTrue(thrown.getMessage().contains("groq"));
        assertTrue(thrown.getMessage().contains("gemini"));
    }

    @Test
    void failsWithTheLimitReachedWhenThereAreNoProviders() {
        ProviderChain chain = new ProviderChain(List.of(), new WaitMeter(duration -> {}));

        assertThrows(ModelLimitReachedException.class, () -> chain.judge(REQUEST));
    }

    @Test
    void countsTheCallsAnsweredPerProvider() {
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", atItsLimit("groq")),
                new ProviderChain.Provider("gemini", answering("gemini", "flash")));
        chain.judge(REQUEST);
        chain.judge(REQUEST);

        assertEquals(Map.of("groq", 0L, "gemini", 2L), chain.callsPerProvider());

        chain.startRun();

        assertEquals(Map.of("groq", 0L, "gemini", 0L), chain.callsPerProvider());
    }

    @Test
    void aFailureOtherThanTheLimitIsNotHiddenByTheNextProvider() {
        ModelCallException failure = new ModelCallException("Provider groq answered 400");
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", request -> {
                    throw failure;
                }),
                new ProviderChain.Provider("gemini", answering("gemini", "flash")));

        ModelCallException thrown = assertThrows(ModelCallException.class, () -> chain.judge(REQUEST));

        assertSame(failure, thrown);
        assertEquals(List.of(), asked);
    }

    @Test
    void namesItsProvidersInOrder() {
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", answering("groq", "a")),
                new ProviderChain.Provider("gemini", answering("gemini", "b")));

        assertEquals(List.of("groq", "gemini"), chain.providerNames());
    }

    @Test
    void startsARunOnlyWhenAProviderHasAKey() {
        ProviderChain withoutProviders = new ProviderChain(List.of(), new WaitMeter(duration -> {}));

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, withoutProviders::startRunRequiringAProvider);

        assertTrue(failure.getMessage().contains("LLM_PROVIDER_1_API_KEY"));
    }

    @Test
    void startsAFreshRunWhenAProviderHasAKey() {
        ProviderChain chain = chainOf(new ProviderChain.Provider("groq", atItsLimit("groq")));
        assertThrows(ModelLimitReachedException.class, () -> chain.judge(REQUEST));

        chain.startRunRequiringAProvider();

        assertThrows(ModelLimitReachedException.class, () -> chain.judge(REQUEST));
        assertEquals(List.of("groq", "groq"), asked);
    }

    @Test
    void reportsTheCallsPerProviderAndTheWaitToTheCountsOfTheRun() {
        ProviderChain chain = chainOf(
                new ProviderChain.Provider("groq", atItsLimit("groq")),
                new ProviderChain.Provider("gemini", answering("gemini", "flash")));
        chain.judge(REQUEST);
        Map<String, Long> counts = new HashMap<>();

        chain.reportUseTo((name, amount) -> counts.merge(name, amount, Long::sum));

        assertEquals(Map.of("calls_groq", 0L, "calls_gemini", 1L, "rate_limit_wait_seconds", 0L), counts);
    }

    @Test
    void passesARefusedAnswerOnToEveryProvider() {
        FakeJudgmentModel groq = new FakeJudgmentModel();
        FakeJudgmentModel exchange = new FakeJudgmentModel();
        ProviderChain chain =
                chainOf(new ProviderChain.Provider("groq", groq), new ProviderChain.Provider("claude-code", exchange));

        chain.answerRefused(REQUEST, "facts are missing");

        assertEquals(List.of("facts are missing"), groq.refusalReasons());
        assertEquals(List.of("facts are missing"), exchange.refusalReasons());
    }
}
