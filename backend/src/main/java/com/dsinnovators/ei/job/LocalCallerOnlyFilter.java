package com.dsinnovators.ei.job;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets only callers on the machine the backend runs on reach the job endpoints (decision 44) and the
 * model exchange (decision 271). The
 * PoC has no authentication; this guard is replaced when it is built. A proxy in front of the
 * backend would make every caller look local.
 */
@Component
class LocalCallerOnlyFilter extends OncePerRequestFilter {

    static final String JOBS_PATH = "/api/jobs";
    static final String MODEL_EXCHANGE_PATH = "/api/model-exchange";

    private static final String REFUSED_BODY = "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
            + "\"detail\":\"Jobs and the model exchange can only be reached from the machine the backend runs on\"}";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !isUnder(path, JOBS_PATH) && !isUnder(path, MODEL_EXCHANGE_PATH);
    }

    private static boolean isUnder(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isLoopback(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(REFUSED_BODY);
    }

    // The remote address is always an IP literal, so this never looks up a name.
    private static boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException notAnAddress) {
            return false;
        }
    }
}
