package com.prism.gateway.services;

import com.prism.gateway.model.ChatCompletionRequest;
import com.prism.gateway.model.ChatCompletionResponse;
import com.prism.gateway.model.VirtualKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.Collection.QueryResponse;
import tech.amikos.chromadb.embeddings.DefaultEmbeddingFunction;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final RedisCacheService redisCacheService;
    private final Collection semanticCacheCollection;
    private final DefaultEmbeddingFunction embeddingFunction;

    @Value("${prism.chroma.similarity-threshold:0.85}")
    private double defaultSimilarityThreshold;

    public ChatCompletionResponse get(
            ChatCompletionRequest request,
            VirtualKey virtualKey
    ) {

        if (!isEnabled(virtualKey)) {
            log.info(
                    "Semantic cache disabled | key={}",
                    virtualKey.getVirtualKey()
            );
            return null;
        }

        String query = buildQuery(request);

        if (query.isBlank()) {
            return null;
        }

        try {

            QueryResponse result =
                    semanticCacheCollection.query(
                            Collections.singletonList(query),
                            1,
                            buildTenantFilter(virtualKey),
                            null,
                            null
                    );

            if (result == null
                    || result.getIds() == null
                    || result.getIds().isEmpty()
                    || result.getIds().get(0) == null
                    || result.getIds().get(0).isEmpty()) {

                log.info(
                        "Semantic cache MISS | no matching vector | key={}",
                        virtualKey.getVirtualKey()
                );

                return null;
            }

            List<List<String>> ids = result.getIds();

            List<List<Float>> distances = result.getDistances();

            if (distances == null
                    || distances.isEmpty()
                    || distances.get(0) == null
                    || distances.get(0).isEmpty()) {

                log.info(
                        "Semantic cache MISS | no distance returned | key={}",
                        virtualKey.getVirtualKey()
                );

                return null;
            }

            String cacheEntryId =
                    ids.get(0).get(0);

            double distance =
                    distances.get(0).get(0);

            double threshold =
                    getSimilarityThreshold(virtualKey);

            log.info(
                    "Semantic cache lookup | key={} | distance={} | threshold={}",
                    virtualKey.getVirtualKey(),
                    distance,
                    threshold
            );

            if (distance > threshold) {

                log.info(
                        "Semantic cache MISS | distance={} > threshold={} | key={}",
                        distance,
                        threshold,
                        virtualKey.getVirtualKey()
                );

                return null;
            }

            ChatCompletionResponse cached =
                    redisCacheService.get(
                            cacheEntryId,
                            virtualKey
                    );

            if (cached == null) {

                log.warn(
                        "Semantic cache MISS | Chroma matched but Redis entry missing | id={} | key={}",
                        cacheEntryId,
                        virtualKey.getVirtualKey()
                );

                return null;
            }

            log.info(
                    "Semantic cache HIT | distance={} <= threshold={} | key={}",
                    distance,
                    threshold,
                    virtualKey.getVirtualKey()
            );

            return cached;

        } catch (Exception e) {

            log.warn(
                    "Semantic cache lookup failed. Treating as MISS | key={} | error={}",
                    virtualKey.getVirtualKey(),
                    e.getMessage()
            );

            return null;
        }
    }

    public void put(
            ChatCompletionRequest request,
            VirtualKey virtualKey,
            ChatCompletionResponse response
    ) {

        if (!isEnabled(virtualKey)) {
            return;
        }

        String query = buildQuery(request);

        if (query.isBlank()) {
            return;
        }

        String cacheEntryId =
                UUID.randomUUID().toString();

        try {

            semanticCacheCollection.add(
                    null,
                    Collections.singletonList(
                            Map.of(
                                    "virtual_key",
                                    virtualKey.getVirtualKey()
                            )
                    ),
                    Collections.singletonList(query),
                    Collections.singletonList(cacheEntryId)
            );

            redisCacheService.put(
                    cacheEntryId,
                    query,
                    virtualKey,
                    response
            );

            log.info(
                    "Semantic cache STORE | key={} | entryId={}",
                    virtualKey.getVirtualKey(),
                    cacheEntryId
            );

        } catch (Exception e) {

            log.warn(
                    "Semantic cache store failed | key={} | error={}",
                    virtualKey.getVirtualKey(),
                    e.getMessage()
            );
        }
    }

    private boolean isEnabled(
            VirtualKey virtualKey
    ) {

        return virtualKey.getSemanticCache() != null
                && virtualKey.getSemanticCache().isEnabled();
    }

    private double getSimilarityThreshold(
            VirtualKey virtualKey
    ) {

        if (virtualKey.getSemanticCache() != null
                && virtualKey.getSemanticCache().getSimilarityThreshold() != null) {

            return virtualKey
                    .getSemanticCache()
                    .getSimilarityThreshold();
        }

        return defaultSimilarityThreshold;
    }

    private String buildQuery(
            ChatCompletionRequest request
    ) {

        StringBuilder query =
                new StringBuilder();

        if (request.getMessages() == null) {
            return "";
        }

        for (ChatCompletionRequest.Message message :
                request.getMessages()) {

            if (message.getContent() == null) {
                continue;
            }

            query.append(message.getRole())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }

        return query.toString().trim();
    }

    private Map<String, Object> buildTenantFilter(
            VirtualKey virtualKey
    ) {

        return Map.of(
                "virtual_key",
                virtualKey.getVirtualKey()
        );
    }
}