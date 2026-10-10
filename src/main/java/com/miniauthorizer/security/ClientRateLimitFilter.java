package com.miniauthorizer.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Token bucket per authenticated API client, so one misbehaving client (a retry storm, a bug,
 * a stolen credential) cannot take the authorizer away from the others.
 * <p>
 * Buckets live in this instance's memory, so the effective limit grows with the number of
 * instances. It is a safety net; the global limit per client belongs to the API gateway, which
 * sees every instance's traffic. Runs after authorization, so only identified clients are counted;
 * anonymous requests are limited by the failed-login lockout instead.
 */
public class ClientRateLimitFilter extends OncePerRequestFilter {

    private static final Map<Boolean, Decision> DECISIONS = Map.of(true, Decision.ALLOW, false, Decision.THROTTLE);

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties limits;

    public ClientRateLimitFilter(RateLimitProperties limits) {
        this.limits = limits;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<ConsumptionProbe> probe = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
                .map(client -> buckets.computeIfAbsent(client.getName(), name -> newBucket()))
                .map(bucket -> bucket.tryConsumeAndReturnRemaining(1));

        probe.map(ConsumptionProbe::isConsumed)
                .map(DECISIONS::get)
                .orElse(Decision.ALLOW)
                .apply(request, response, chain, probe);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limits.burst())
                        .refillGreedy(limits.requestsPerSecond(), Duration.ofSeconds(1))
                        .build())
                .build();
    }

    private enum Decision {

        ALLOW {
            @Override
            void apply(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                       Optional<ConsumptionProbe> probe) throws ServletException, IOException {
                chain.doFilter(request, response);
            }
        },

        THROTTLE {
            @Override
            void apply(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                       Optional<ConsumptionProbe> probe) throws IOException {
                long waitSeconds = probe.map(ConsumptionProbe::getNanosToWaitForRefill)
                        .map(nanos -> Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanos + 999_999_999)))
                        .orElse(1L);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(waitSeconds));
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,"
                        + "\"detail\":\"Request rate limit exceeded for this client\"}");
            }
        };

        abstract void apply(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                            Optional<ConsumptionProbe> probe) throws ServletException, IOException;
    }
}
