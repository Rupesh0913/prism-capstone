package com.prism.gateway.services;

import com.prism.gateway.model.CacheEntry;
import com.prism.gateway.model.ChatCompletionResponse;
import com.prism.gateway.model.VirtualKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration CACHE_TTL =
            Duration.ofHours(1);

    public ChatCompletionResponse get(
            String cacheEntryId,
            VirtualKey virtualKey
    ) {

        String redisKey =
                buildRedisKey(virtualKey, cacheEntryId);

        String json =
                redisTemplate.opsForValue().get(redisKey);

        if (json == null) {
            return null;
        }

        try {

            CacheEntry entry =
                    objectMapper.readValue(
                            json,
                            CacheEntry.class
                    );

            return entry.getResponse();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to deserialize cached response",
                    e
            );
        }
    }

    public void put(
            String cacheEntryId,
            String query,
            VirtualKey virtualKey,
            ChatCompletionResponse response
    ) {

        String redisKey =
                buildRedisKey(
                        virtualKey,
                        cacheEntryId
                );

        try {

            CacheEntry entry =
                    new CacheEntry(
                            cacheEntryId,
                            query,
                            response
                    );

            String json =
                    objectMapper.writeValueAsString(entry);

            redisTemplate.opsForValue().set(
                    redisKey,
                    json,
                    CACHE_TTL
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to serialize cached response",
                    e
            );
        }
    }

    private String buildRedisKey(
            VirtualKey virtualKey,
            String cacheEntryId
    ) {

        return "prism:semantic-cache:"
                + virtualKey.getVirtualKey()
                + ":"
                + cacheEntryId;
    }
}