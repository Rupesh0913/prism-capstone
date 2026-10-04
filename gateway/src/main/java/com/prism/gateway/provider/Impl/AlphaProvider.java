package com.prism.gateway.provider.Impl;

import com.prism.gateway.model.ChatCompletionChunk;
import com.prism.gateway.model.ChatCompletionRequest;
import com.prism.gateway.model.ChatCompletionResponse;
import com.prism.gateway.provider.LlmProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;


@Service
public class AlphaProvider implements LlmProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    @Value("${prism.providers.alpha.base-url}")
    private String baseUrl;

    public AlphaProvider(@Qualifier("alphaWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ChatCompletionResponse> complete(ChatCompletionRequest request) {
        return webClient
                .post()
                .uri(baseUrl + "/v1/chat/completions")
                .header("Authorization", "Bearer prism-local")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class);
    }

    @Override
    public Flux<ChatCompletionChunk> stream(ChatCompletionRequest request) {
        return webClient
                .post()
                .uri(baseUrl + "/v1/chat/completions")
                .header("Authorization", "Bearer prism-local")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(data -> data != null && !data.equals("[DONE]"))
                .map(data -> {
                    try {
                        return objectMapper.readValue(data, ChatCompletionChunk.class);
                    } catch (JacksonException e) {
                        throw new IllegalStateException("Invalid provider stream chunk", e);
                    }
                });
    }
}
