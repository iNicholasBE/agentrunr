package io.agentrunr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API key authentication filter.
 *
 * <p>When enabled, requires an {@code X-API-Key} header (or {@code api_key} query parameter)
 * matching the configured key. Health endpoint is always accessible.</p>
 *
 * <p>Configure in application.yml:</p>
 * <pre>
 * agent:
 *   security:
 *     api-key: ${AGENT_API_KEY:}
 * </pre>
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "agent.security.api-key")
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_PARAM = "api_key";

    @Value("${agent.security.api-key}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Always allow health checks
        if (path.equals("/api/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip non-API paths (dashboard, etc.)
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check API key
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null) {
            providedKey = request.getParameter(API_KEY_PARAM);
        }

        if (apiKey.equals(providedKey)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
        }
    }
}
