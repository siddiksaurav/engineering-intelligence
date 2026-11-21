package com.dsinnovators.ei.judgment;

import static com.dsinnovators.ei.judgment.ModelApiFixture.CHAT_COMPLETIONS;
import static com.dsinnovators.ei.judgment.ModelApiFixture.KEY;
import static com.dsinnovators.ei.judgment.ModelApiFixture.completion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dsinnovators.ei.common.CanonicalJson;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.response.DefaultResponseCreator;

class OpenAiCompatibleClientTest {

    private static final String ANSWER = "{\"workType\":\"fix\"}";

    private final ModelApiFixture fixture = new ModelApiFixture();

    private static ModelRequest request() {
        return new ModelRequest(
                "pull-request-digest/v1",
                "Digest the pull request.",
                "Title: Fix the login",
                "pull_request_digest",
                CanonicalJson.parse("{\"type\":\"object\",\"properties\":{\"workType\":{\"type\":\"string\"}}}"));
    }

    private static DefaultResponseCreator success() {
        return withSuccess(completion("openai/gpt-oss-120b-0925", ANSWER, 1200, 340), MediaType.APPLICATION_JSON);
    }

    private static DefaultResponseCreator tooManyRequests(String headerName, String headerValue) {
        return withStatus(HttpStatus.TOO_MANY_REQUESTS).header(headerName, headerValue);
    }

    @Test
    void sendsTheStrictSchemaTheModelAndTheReasoningEffort() {
        fixture.server
                .expect(requestTo(CHAT_COMPLETIONS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Digest the pull request."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Title: Fix the login"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("pull_request_digest"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.workType.type")
                        .value("string"))
                .andRespond(success());

        fixture.client.judge(request());

        fixture.server.verify();
    }

    @Test
    void leavesOutTheReasoningEffortWhenTheProviderHasNone() {
        ModelApiFixture withoutEffort = new ModelApiFixture(
                new JudgmentProvider("gemini", "https://api.groq.com/openai/v1/", KEY, "gemini-2.5-flash", null));
        withoutEffort
                .server
                .expect(requestTo(CHAT_COMPLETIONS))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andExpect(jsonPath("$.model").value("gemini-2.5-flash"))
                .andRespond(success());

        withoutEffort.client.judge(request());

        withoutEffort.server.verify();
    }

    @Test
    void returnsTheAnswerTheModelTheAnswerNamesAndTheTokenUsage() {
        fixture.server.expect(requestTo(CHAT_COMPLETIONS)).andRespond(success());

        ModelAnswer answer = fixture.client.judge(request());

        assertEquals(ANSWER, answer.answer().toString());
        assertEquals("openai/gpt-oss-120b-0925", answer.model());
        assertEquals(1200, answer.inputTokens());
        assertEquals(340, answer.outputTokens());
    }

    @Test
    void waitsOutAShortRateLimitFromRetryAfterAndTriesAgain() {
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(tooManyRequests("retry-after", "7"));
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(success());

        ModelAnswer answer = fixture.client.judge(request());

        assertEquals(ANSWER, answer.answer().toString());
        assertEquals(List.of(Duration.ofSeconds(7)), fixture.waits);
    }

    @Test
    void waitsOutAShortRateLimitFromTheLongestResetHeaderWhenThereIsNoRetryAfter() {
        fixture.server
                .expect(once(), requestTo(CHAT_COMPLETIONS))
                .andRespond(tooManyRequests("x-ratelimit-reset-tokens", "45.56s")
                        .header("x-ratelimit-reset-requests", "1.5s"));
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(success());

        fixture.client.judge(request());

        assertEquals(List.of(Duration.ofMillis(45_560)), fixture.waits);
    }

    private static DefaultResponseCreator successWithTokenWindow(String remaining, String reset) {
        return success().header("x-ratelimit-remaining-tokens", remaining).header("x-ratelimit-reset-tokens", reset);
    }

    @Test
    void waitsForTheTokenWindowToResetWhenTheNextRequestWouldNotFit() {
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(successWithTokenWindow("900", "7.66s"));
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(success());

        fixture.client.judge(request());
        fixture.client.judge(request());

        assertEquals(List.of(Duration.ofMillis(7_660)), fixture.waits);
    }

    @Test
    void readsAResetWrittenInMinutesAndSeconds() {
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(successWithTokenWindow("0", "2m59.56s"));
        fixture.client.judge(request());

        ProviderLimitReachedException thrown =
                assertThrows(ProviderLimitReachedException.class, () -> fixture.client.judge(request()));

        assertTrue(thrown.getMessage().contains("179 s"));
    }

    @Test
    void doesNotWaitWhenTheNextRequestFits() {
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(successWithTokenWindow("6000", "30s"));
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(success());

        fixture.client.judge(request());
        fixture.client.judge(request());

        assertEquals(List.of(), fixture.waits);
    }

    @Test
    void reportsTheProviderLimitWhenTheWindowResetsLaterThanTheLongestWait() {
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(successWithTokenWindow("0", "2h"));
        fixture.client.judge(request());

        ProviderLimitReachedException thrown =
                assertThrows(ProviderLimitReachedException.class, () -> fixture.client.judge(request()));

        assertEquals("groq", thrown.providerName());
        assertEquals(List.of(), fixture.waits);
    }

    @Test
    void waitsTheConfiguredRetryWaitWhenTheRateLimitSaysNothingAboutTime() {
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(success());

        fixture.client.judge(request());

        assertEquals(List.of(Duration.ofSeconds(2)), fixture.waits);
    }

    @Test
    void reportsALongRateLimitAsTheLimitOfTheProviderWithoutWaiting() {
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(tooManyRequests("retry-after", "3600"));

        ProviderLimitReachedException thrown =
                assertThrows(ProviderLimitReachedException.class, () -> fixture.client.judge(request()));

        assertEquals("groq", thrown.providerName());
        assertTrue(thrown.getMessage().contains("groq"));
        assertTrue(thrown.getMessage().contains("3600"));
        assertEquals(List.of(), fixture.waits);
    }

    @Test
    void reportsTheProviderLimitWhenTheRateLimitStaysAfterTheRetries() {
        fixture.server.expect(times(3), requestTo(CHAT_COMPLETIONS)).andRespond(tooManyRequests("retry-after", "1"));

        assertThrows(ProviderLimitReachedException.class, () -> fixture.client.judge(request()));

        assertEquals(2, fixture.waits.size());
    }

    @Test
    void aRequestThatIsTooLargeFailsAtOnceWithItsStatus() {
        fixture.server
                .expect(once(), requestTo(CHAT_COMPLETIONS))
                .andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE).body("Request too large"));

        ModelCallException thrown = assertThrows(ModelCallException.class, () -> fixture.client.judge(request()));

        assertTrue(thrown.getMessage().contains("413"));
        assertTrue(thrown.getMessage().contains("groq"));
        assertEquals(List.of(), fixture.waits);
    }

    @Test
    void aBadRequestFailsAtOnceWithItsStatusAndWhatTheProviderSaid() {
        fixture.server
                .expect(once(), requestTo(CHAT_COMPLETIONS))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("schema is not strict"));

        ModelCallException thrown = assertThrows(ModelCallException.class, () -> fixture.client.judge(request()));

        assertTrue(thrown.getMessage().contains("400"));
        assertTrue(thrown.getMessage().contains("schema is not strict"));
    }

    @Test
    void aRefusedKeyFailsWithTheStatusAndNeverShowsTheKey() {
        fixture.server
                .expect(once(), requestTo(CHAT_COMPLETIONS))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("invalid key"));

        ModelCallException thrown = assertThrows(ModelCallException.class, () -> fixture.client.judge(request()));

        assertTrue(thrown.getMessage().contains("401"));
        assertFalse(thrown.getMessage().contains(KEY));
    }

    @Test
    void aServerErrorIsTriedAgainAndThenAnswers() {
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        fixture.server.expect(once(), requestTo(CHAT_COMPLETIONS)).andRespond(success());

        fixture.client.judge(request());

        assertEquals(List.of(Duration.ofSeconds(2)), fixture.waits);
    }

    @Test
    void aServerErrorAfterTheRetriesFailsWithTheStatus() {
        fixture.server
                .expect(times(3), requestTo(CHAT_COMPLETIONS))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ModelCallException thrown = assertThrows(ModelCallException.class, () -> fixture.client.judge(request()));

        assertTrue(thrown.getMessage().contains("503"));
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)), fixture.waits);
    }

    @Test
    void aTimeoutIsTriedAgainAndThenFailsWithoutTheKey() {
        fixture.server
                .expect(times(3), requestTo(CHAT_COMPLETIONS))
                .andRespond(withException(new IOException("Read timed out")));

        ModelCallException thrown = assertThrows(ModelCallException.class, () -> fixture.client.judge(request()));

        assertTrue(thrown.getMessage().contains("groq"));
        assertFalse(thrown.getMessage().contains(KEY));
        assertNotNull(thrown.getCause());
    }

    @Test
    void anAnswerThatIsNotJsonFails() {
        fixture.server
                .expect(requestTo(CHAT_COMPLETIONS))
                .andRespond(withSuccess(completion("m", "not json at all", 1, 1), MediaType.APPLICATION_JSON));

        ModelCallException thrown = assertThrows(ModelCallException.class, () -> fixture.client.judge(request()));

        assertTrue(thrown.getMessage().contains("not JSON"));
    }

    @Test
    void anAnswerCutOffByTheTokenLimitFails() {
        fixture.server
                .expect(requestTo(CHAT_COMPLETIONS))
                .andRespond(withSuccess(
                        completion("m", ANSWER, 1, 1).replace("\"stop\"", "\"length\""), MediaType.APPLICATION_JSON));

        ModelCallException thrown = assertThrows(ModelCallException.class, () -> fixture.client.judge(request()));

        assertTrue(thrown.getMessage().contains("cut off"));
    }

    @Test
    void anAnswerWithoutAMessageFails() {
        fixture.server
                .expect(requestTo(CHAT_COMPLETIONS))
                .andRespond(withSuccess("{\"model\":\"m\",\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(ModelCallException.class, () -> fixture.client.judge(request()));
    }
}
