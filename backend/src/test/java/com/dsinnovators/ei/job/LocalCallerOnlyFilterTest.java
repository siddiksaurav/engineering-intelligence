package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LocalCallerOnlyFilterTest {

    private final LocalCallerOnlyFilter filter = new LocalCallerOnlyFilter();

    private MockFilterChain call(String path, String remoteAddress, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddress);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain;
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.20", "10.0.0.7", "2001:db8::1"})
    void aCallerFromAnotherMachineIsRefused(String remoteAddress) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = call("/api/jobs/github-import/runs", remoteAddress, response);

        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertTrue(response.getContentAsString()
                .contains("Jobs and the model exchange can only be reached from the machine the backend runs on"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/model-exchange/requests", "/api/model-exchange/requests/abc/answer"})
    void theModelExchangeIsGuardedLikeTheJobs(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = call(path, "192.168.1.20", response);

        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.0.1.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1"})
    void aCallerOnTheSameMachineIsLetThrough(String remoteAddress) throws Exception {
        MockFilterChain chain = call("/api/jobs/github-import/runs", remoteAddress, new MockHttpServletResponse());

        assertNotNull(chain.getRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/api/jobsx", "/api/model-exchangex"})
    void otherPathsAreNotGuarded(String path) throws Exception {
        MockFilterChain chain = call(path, "192.168.1.20", new MockHttpServletResponse());

        assertNotNull(chain.getRequest());
    }
}
