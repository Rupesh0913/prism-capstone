package com.prism.gateway.services;
import com.prism.gateway.exception.RateLimitExceededException;
import com.prism.gateway.model.VirtualKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final Duration WINDOW =
            Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    /**
     * Checks and reserves one request from the
     * requests-per-minute quota.
     */
    public void checkRequestLimit(VirtualKey virtualKey) {

        validateConfiguration(virtualKey);

        String key = buildRequestKey(virtualKey);

        Long currentCount =
                redisTemplate.opsForValue().increment(key);

        if (currentCount == null) {
            throw new IllegalStateException(
                    "Unable to increment request rate-limit counter"
            );
        }

        /*
         * The first request creates the key.
         * Only the first request should establish
         * the 60-second expiration window.
         */
        if (currentCount == 1L) {
            redisTemplate.expire(key, WINDOW);
        }

        int limit =
                virtualKey.getRateLimit()
                        .getRequestsPerMinute();

        if (currentCount > limit) {

            /*
             * We already incremented the counter,
             * so roll it back for a rejected request.
             */
            redisTemplate.opsForValue().decrement(key);

            throw new RateLimitExceededException(
                    "Requests per minute limit exceeded"
            );
        }
    }

    /**
     * Adds provider token usage to the
     * tokens-per-minute quota.
     */
    public void addTokens(
            VirtualKey virtualKey,
            int tokens
    ) {

        validateConfiguration(virtualKey);

        if (tokens <= 0) {
            return;
        }

        String key = buildTokenKey(virtualKey);

        Long currentTokens =
                redisTemplate.opsForValue()
                        .increment(key, tokens);

        if (currentTokens == null) {
            throw new IllegalStateException(
                    "Unable to increment token rate-limit counter"
            );
        }

        /*
         * First token usage establishes the
         * 60-second window.
         */
        if (currentTokens == tokens) {
            redisTemplate.expire(key, WINDOW);
        }

        int limit =
                virtualKey.getRateLimit()
                        .getTokensPerMinute();

        if (currentTokens > limit) {

            /*
             * Roll back the token reservation because
             * this request exceeded the TPM limit.
             */
            redisTemplate.opsForValue()
                    .decrement(key, tokens);

            throw new RateLimitExceededException(
                    "Tokens per minute limit exceeded"
            );
        }
    }

    private String buildRequestKey(
            VirtualKey virtualKey
    ) {

        return "prism:rate-limit:"
                + virtualKey.getVirtualKey()
                + ":requests";
    }

    private String buildTokenKey(
            VirtualKey virtualKey
    ) {

        return "prism:rate-limit:"
                + virtualKey.getVirtualKey()
                + ":tokens";
    }

    private void validateConfiguration(
            VirtualKey virtualKey
    ) {

        if (virtualKey == null) {
            throw new IllegalArgumentException(
                    "Virtual key cannot be null"
            );
        }

        if (virtualKey.getRateLimit() == null) {
            throw new IllegalArgumentException(
                    "Rate limit configuration is missing"
            );
        }

        if (virtualKey.getRateLimit()
                .getRequestsPerMinute() <= 0) {

            throw new IllegalArgumentException(
                    "Requests per minute limit must be greater than zero"
            );
        }

        if (virtualKey.getRateLimit()
                .getTokensPerMinute() <= 0) {

            throw new IllegalArgumentException(
                    "Tokens per minute limit must be greater than zero"
            );
        }
    }
}
