package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.JsonNode;

class OpenProjectApiTest {

    private static final String WORK_PACKAGE = OpenProjectApiFixture.API_URL + "/work_packages/81224";
    private static final Duration RETRY_WAIT = OpenProjectApiFixture.RETRY_WAIT;
    private static final Duration PAUSE = OpenProjectApiFixture.PAUSE;

    private final OpenProjectApiFixture fixture = new OpenProjectApiFixture();
    private final List<Duration> waits = fixture.waits;

    private static String recorded(String name) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource("openproject/" + name).getInputStream(), StandardCharsets.UTF_8);
    }

    private void expectGet(String url, org.springframework.test.web.client.response.DefaultResponseCreator answer) {
        fixture.server.expect(requestTo(url)).andExpect(method(HttpMethod.GET)).andRespond(answer);
    }

    @Test
    void retriesAServiceUnavailableAnswerAndThenReturnsTheBody() throws Exception {
        expectGet(WORK_PACKAGE, withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        expectGet(WORK_PACKAGE, withSuccess(recorded("work-package-81224.json"), MediaType.APPLICATION_JSON));

        JsonNode body = fixture.api.get("/work_packages/81224");

        assertEquals(81224, body.path("id").asInt());
        assertEquals(List.of(RETRY_WAIT), waits);
        fixture.server.verify();
    }

    @Test
    void sendsAPlainGetWithAGenericUserAgentAndNoLogin() throws Exception {
        fixture.server
                .expect(requestTo(WORK_PACKAGE))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "engineering-intelligence-poc"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
                .andRespond(withSuccess("{\"id\":81224}", MediaType.APPLICATION_JSON));

        fixture.api.get("/work_packages/81224");

        fixture.server.verify();
    }

    @Test
    void keepsTheDigitsOfNumbersExactlyAsOpenProjectSentThem() throws Exception {
        expectGet(WORK_PACKAGE, withSuccess("{\"percentageDone\":12.50}", MediaType.APPLICATION_JSON));

        assertEquals(
                "12.50",
                fixture.api
                        .get("/work_packages/81224")
                        .path("percentageDone")
                        .decimalValue()
                        .toPlainString());
    }

    @Test
    void waitsThePauseBetweenTwoCallsButNotBeforeTheFirst() throws Exception {
        fixture.server
                .expect(times(2), requestTo(WORK_PACKAGE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        fixture.api.get("/work_packages/81224");
        assertTrue(waits.isEmpty());
        fixture.api.get("/work_packages/81224");

        assertEquals(List.of(PAUSE), waits);
    }

    @Test
    void waitsTheRetryAfterOfATooManyRequestsAnswerThenTriesAgain() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "7");
        expectGet(WORK_PACKAGE, withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
        expectGet(WORK_PACKAGE, withSuccess("{}", MediaType.APPLICATION_JSON));

        fixture.api.get("/work_packages/81224");

        assertEquals(List.of(Duration.ofSeconds(7)), waits);
    }

    @Test
    void waitsGrowingTimesWhenATooManyRequestsAnswerHasNoRetryAfter() throws Exception {
        expectGet(WORK_PACKAGE, withStatus(HttpStatus.TOO_MANY_REQUESTS));
        expectGet(WORK_PACKAGE, withStatus(HttpStatus.TOO_MANY_REQUESTS));
        expectGet(WORK_PACKAGE, withSuccess("{}", MediaType.APPLICATION_JSON));

        fixture.api.get("/work_packages/81224");

        assertEquals(List.of(RETRY_WAIT, RETRY_WAIT.multipliedBy(2)), waits);
    }

    @Test
    void retriesATimeoutAndFailsAfterTheLastRetry() {
        fixture.server
                .expect(times(4), requestTo(WORK_PACKAGE))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> fixture.api.get("/work_packages/81224"));

        assertTrue(failure.getMessage().contains("GET /work_packages/81224"));
        assertTrue(failure.getMessage().contains("4 attempts"));
        assertEquals(List.of(RETRY_WAIT, RETRY_WAIT.multipliedBy(2), RETRY_WAIT.multipliedBy(3)), waits);
    }

    @Test
    void failsAfterTheLastRetryOfAServerError() {
        fixture.server.expect(times(4), requestTo(WORK_PACKAGE)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> fixture.api.get("/work_packages/81224"));

        assertTrue(failure.getMessage().contains("502"));
        fixture.server.verify();
    }

    @Test
    void failsAfterTheLastRetryOfATooManyRequestsAnswer() {
        fixture.server.expect(times(4), requestTo(WORK_PACKAGE)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> fixture.api.get("/work_packages/81224"));

        assertTrue(failure.getMessage().contains("limiting the rate"));
    }

    @Test
    void reportsANotFoundAnswerAsItsOwnOutcomeWithoutRetry() {
        expectGet(WORK_PACKAGE, withStatus(HttpStatus.NOT_FOUND));

        OpenProjectNotFoundException failure =
                assertThrows(OpenProjectNotFoundException.class, () -> fixture.api.get("/work_packages/81224"));

        assertTrue(failure.getMessage().contains("GET /work_packages/81224"));
        assertTrue(waits.isEmpty());
        fixture.server.verify();
    }

    @Test
    void reportsABadRequestAnswerAsItsOwnOutcomeWithoutRetry() {
        String path = "/work_packages?filters=%5B%5D";
        expectGet(OpenProjectApiFixture.API_URL + path, withStatus(HttpStatus.BAD_REQUEST));

        OpenProjectBadRequestException failure =
                assertThrows(OpenProjectBadRequestException.class, () -> fixture.api.get(path));

        assertTrue(failure.getMessage().contains(path));
        assertInstanceOf(OpenProjectApiException.class, failure);
        fixture.server.verify();
    }

    @Test
    void failsOnAnyOtherClientErrorWithoutRetry() {
        expectGet(WORK_PACKAGE, withStatus(HttpStatus.FORBIDDEN));

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> fixture.api.get("/work_packages/81224"));

        assertTrue(failure.getMessage().contains("403"));
        assertFalse(failure instanceof OpenProjectNotFoundException);
        assertTrue(waits.isEmpty());
    }

    @Test
    void failsWhenTheAnswerIsNotJson() {
        expectGet(WORK_PACKAGE, withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> fixture.api.get("/work_packages/81224"));

        assertTrue(failure.getMessage().contains("not JSON"));
    }

    @Test
    void failsWhenTheAnswerIsNotAnObject() {
        expectGet(WORK_PACKAGE, withSuccess("[1,2]", MediaType.APPLICATION_JSON));

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> fixture.api.get("/work_packages/81224"));

        assertTrue(failure.getMessage().contains("not a JSON object"));
    }

    @Test
    void refusesAPathThatDoesNotStartWithASlash() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> fixture.api.get("work_packages/81224"));

        assertTrue(failure.getMessage().contains("work_packages/81224"));
    }
}
