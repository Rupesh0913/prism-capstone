package com.prism.gateway.controller;

import com.prism.gateway.exception.AuthenticationException;
import com.prism.gateway.exception.BudgetExceededException;
import com.prism.gateway.exception.ModelNotAllowedException;
import com.prism.gateway.exception.RateLimitExceededException;
import com.prism.gateway.model.ChatCompletionChunk;
import com.prism.gateway.model.ChatCompletionRequest;
import com.prism.gateway.model.ChatCompletionResponse;
import com.prism.gateway.model.VirtualKey;
import com.prism.gateway.provider.Impl.AlphaProvider;
import com.prism.gateway.provider.LlmProvider;
import com.prism.gateway.services.BudgetService;
import com.prism.gateway.services.GatewayService;
import com.prism.gateway.services.RateLimitService;
import com.prism.gateway.services.VirtualKeyService;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
public class ChatCompletionController {

    private final GatewayService gatewayService;
    private final ObjectMapper objectMapper;
    private final VirtualKeyService virtualKeyService;
    private final RateLimitService rateLimitService;
    private final BudgetService budgetService;


    public ChatCompletionController(GatewayService gatewayService, ObjectMapper objectMapper, VirtualKeyService virtualKeyService, RateLimitService rateLimitService, BudgetService budgetService) {
        this.gatewayService = gatewayService;
        this.objectMapper = objectMapper;
        this.virtualKeyService = virtualKeyService;
        this.rateLimitService = rateLimitService;
        this.budgetService = budgetService;
    }

    @PostMapping("/v1/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletion(@RequestBody ChatCompletionRequest request,
                                                  @RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @RequestHeader(value = "x-prism-provider", required = false) String provider,
                                                  @RequestHeader(value = "x-prism-fallback", defaultValue = "false") boolean fallback) {

        VirtualKey virtualKey;
        try {
            virtualKey = virtualKeyService.authenticate(authorization);
            virtualKeyService.validateModel(virtualKey, request.getModel());
            rateLimitService.checkRequestLimit(virtualKey);
            budgetService.checkBudget(virtualKey);
        } catch (AuthenticationException e) {

            return Mono.just(
                    ResponseEntity
                            .status(HttpStatus.UNAUTHORIZED)
                            .body(e.getMessage())
            );

        } catch (ModelNotAllowedException e) {

            return Mono.just(
                    ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body(e.getMessage())
            );
        } catch (RateLimitExceededException e) {

            return Mono.just(
                    ResponseEntity
                            .status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(e.getMessage())
            );
        } catch (BudgetExceededException e) {

            return Mono.just(
                    ResponseEntity
                            .status(HttpStatus.PAYMENT_REQUIRED)
                            .body(e.getMessage())
            );
        }

        // Streaming request
        if (request.isStream()) {

            return gatewayService
                    .stream(request,provider, fallback,virtualKey)
                    .map(result -> {

                                Flux<ServerSentEvent<String>> events = result.getStream()
                                                .map(this::toSseEvent)
                                                .concatWithValues(ServerSentEvent.<String>builder().data("[DONE]").build());
                                return ResponseEntity.ok()
                                    .contentType(MediaType.TEXT_EVENT_STREAM)
                                    .header(
                                        "x-prism-provider",
                                        result.getProvider()
                                    )
                                    .header(
                                        "x-prism-fallback",
                                        String.valueOf(result.isFallback())
                                    )
                                    .body(events);
                    });
        }

        // Normal request
        return gatewayService
                .complete(request,provider, fallback, virtualKey)
                .map(result ->
                        ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "x-prism-provider",
                                        result.getProvider()
                                )
                                .header(
                                        "x-prism-fallback",
                                        String.valueOf(
                                                result.isFallback()
                                        )
                                )
                                .header(
                                        "x-prism-cache",
                                        result.isCacheHit() ? "hit" : "miss"
                                )
                                .header(
                                        "x-prism-cost-usd",
                                        String.valueOf(result.getCost())
                                )
                                .body(result.getResponse())
                );
    }

    private ServerSentEvent<String> toSseEvent(ChatCompletionChunk chunk) {
        try {
            String json = objectMapper.writeValueAsString(chunk);

            return ServerSentEvent.<String>builder()
                    .data(json)
                    .build();

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Failed to serialize streaming chunk", e);
        }
    }
}
