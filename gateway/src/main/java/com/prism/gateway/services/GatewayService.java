package com.prism.gateway.services;

import com.prism.gateway.model.*;
import com.prism.gateway.provider.LlmProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class GatewayService {

    private final LlmProvider alphaProvider;
    private final LlmProvider betaProvider;

    private final ModelRouter modelRouter;
    private final AutoRoutingService autoRoutingService;

    private final RateLimitService rateLimitService;
    private final CostService costService;
    private final BudgetService budgetService;
    private final SemanticCacheService semanticCacheService;

    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY =
            Duration.ofMillis(200);

    public GatewayService(
            @Qualifier("alphaProvider")
            LlmProvider alphaProvider,

            @Qualifier("betaProvider")
            LlmProvider betaProvider,

            ModelRouter modelRouter,
            AutoRoutingService autoRoutingService,
            RateLimitService rateLimitService,
            CostService costService,
            BudgetService budgetService,
            SemanticCacheService semanticCacheService
    ) {
        this.alphaProvider = alphaProvider;
        this.betaProvider = betaProvider;
        this.modelRouter = modelRouter;
        this.autoRoutingService = autoRoutingService;
        this.rateLimitService = rateLimitService;
        this.costService = costService;
        this.budgetService = budgetService;
        this.semanticCacheService = semanticCacheService;
    }

    // =========================================================
    // NORMAL REQUEST
    // =========================================================

    public Mono<GatewayResponse> complete(
            ChatCompletionRequest request,
            String provider,
            boolean fallback,
            VirtualKey virtualKey
    ) {

        log.info(
                "Gateway request | key={} | model={} | provider={} | fallback={}",
                virtualKey.getVirtualKey(),
                request.getModel(),
                provider,
                fallback
        );

        // ---------------------------------------------------------
        // SEMANTIC CACHE
        // ---------------------------------------------------------

        log.info(
                "Checking semantic cache | virtualKey={} | model={}",
                virtualKey.getVirtualKey(),
                request.getModel()
        );

        ChatCompletionResponse cached =
                semanticCacheService.get(
                        request,
                        virtualKey
                );

        if (cached != null) {

            log.info(
                    "SEMANTIC CACHE HIT | virtualKey={} | model={}",
                    virtualKey.getVirtualKey(),
                    request.getModel()
            );

            return Mono.just(
                    new GatewayResponse(
                            cached,
                            "cache",
                            false,
                            0.0,
                            true
                    )
            );
        }

        log.info(
                "SEMANTIC CACHE MISS | virtualKey={} | model={}",
                virtualKey.getVirtualKey(),
                request.getModel()
        );

        // ---------------------------------------------------------
        // PROVIDER REQUEST
        // ---------------------------------------------------------

        return callWithFailover(
                request,
                provider,
                fallback
        )
                .doOnNext(result -> {

                    ChatCompletionResponse response =
                            result.getResponse();

                    if (response == null) {
                        return;
                    }

                    Usage usage =
                            response.getUsage();

                    // -----------------------------------------------------
                    // TOKEN + COST ACCOUNTING
                    // -----------------------------------------------------

                    if (usage != null) {

                        rateLimitService.addTokens(
                                virtualKey,
                                usage.getTotalTokens()
                        );

                        double cost =
                                costService.calculateCost(
                                        response.getModel(),
                                        usage
                                );

                        budgetService.addCost(
                                virtualKey,
                                cost
                        );

                        log.info(
                                "Request cost: ${}, monthly spent: ${}",
                                cost,
                                budgetService.getSpent(virtualKey)
                        );
                    }

                    // -----------------------------------------------------
                    // SEMANTIC CACHE WRITE
                    // -----------------------------------------------------

                    log.info(
                            "Storing response in semantic cache | virtualKey={} | model={}",
                            virtualKey.getVirtualKey(),
                            response.getModel()
                    );

                    semanticCacheService.put(
                            request,
                            virtualKey,
                            response
                    );

                    log.info(
                            "Semantic cache write completed | virtualKey={}",
                            virtualKey.getVirtualKey()
                    );
                });
    }

    // =========================================================
    // PROVIDER FAILOVER
    // =========================================================

    private Mono<GatewayResponse> callWithFailover(
            ChatCompletionRequest request,
            String provider,
            boolean fallback
    ) {

        ModelRoute route =
                resolveRoute(request);

        log.info(
                "Provider routing | requestedProvider={} | fallback={} | primary={} | fallbackModel={}",
                provider,
                fallback,
                route.getPrimaryModel(),
                route.getFallbackModel()
        );

        // =========================================================
        // EXPLICIT PROVIDER
        // =========================================================

        if (provider != null && !provider.isBlank()) {

            // -----------------------------------------------------
            // EXPLICIT ALPHA
            // -----------------------------------------------------

            if ("alpha".equalsIgnoreCase(provider)) {

                return callProviderWithRetry(
                        alphaProvider,
                        withModel(
                                request,
                                route.getPrimaryModel()
                        ),
                        "alpha"
                )
                        .map(response ->
                                createGatewayResponse(
                                        response,
                                        "alpha",
                                        false
                                )
                        )
                        .onErrorResume(error -> {

                            log.error(
                                    "Alpha provider failed | fallback={} | error={}",
                                    fallback,
                                    rootMessage(error)
                            );

                            if (!fallback) {
                                return Mono.error(error);
                            }

                            log.warn(
                                    "ALPHA FAILED -> FALLING BACK TO BETA | model={}",
                                    route.getFallbackModel()
                            );

                            return callProviderWithRetry(
                                    betaProvider,
                                    withModel(
                                            request,
                                            route.getFallbackModel()
                                    ),
                                    "beta"
                            )
                                    .map(response ->
                                            createGatewayResponse(
                                                    response,
                                                    "beta",
                                                    true
                                            )
                                    )
                                    .doOnError(betaError ->
                                            log.error(
                                                    "Beta fallback also failed | error={}",
                                                    rootMessage(betaError)
                                            )
                                    );
                        });
            }

            // -----------------------------------------------------
            // EXPLICIT BETA
            // -----------------------------------------------------

            if ("beta".equalsIgnoreCase(provider)) {

                return callProviderWithRetry(
                        betaProvider,
                        withModel(
                                request,
                                route.getFallbackModel()
                        ),
                        "beta"
                )
                        .map(response ->
                                createGatewayResponse(
                                        response,
                                        "beta",
                                        false
                                )
                        )
                        .onErrorResume(error -> {

                            log.error(
                                    "Beta provider failed | fallback={} | error={}",
                                    fallback,
                                    rootMessage(error)
                            );

                            if (!fallback) {
                                return Mono.error(error);
                            }

                            log.warn(
                                    "BETA FAILED -> FALLING BACK TO ALPHA | model={}",
                                    route.getPrimaryModel()
                            );

                            return callProviderWithRetry(
                                    alphaProvider,
                                    withModel(
                                            request,
                                            route.getPrimaryModel()
                                    ),
                                    "alpha"
                            )
                                    .map(response ->
                                            createGatewayResponse(
                                                    response,
                                                    "alpha",
                                                    true
                                            )
                                    )
                                    .doOnError(alphaError ->
                                            log.error(
                                                    "Alpha fallback also failed | error={}",
                                                    rootMessage(alphaError)
                                            )
                                    );
                        });
            }

            return Mono.error(
                    new IllegalArgumentException(
                            "Unknown provider: " + provider
                    )
            );
        }

        // =========================================================
        // AUTOMATIC PROVIDER SELECTION
        // =========================================================

        log.info(
                "Automatic provider selection | primary={} | fallback={} | fallbackEnabled={}",
                route.getPrimaryModel(),
                route.getFallbackModel(),
                fallback
        );

        return callProviderWithRetry(
                alphaProvider,
                withModel(
                        request,
                        route.getPrimaryModel()
                ),
                "alpha"
        )
                .map(response ->
                        createGatewayResponse(
                                response,
                                "alpha",
                                false
                        )
                )
                .onErrorResume(error -> {

                    log.error(
                            "Primary Alpha provider failed | fallback={} | error={}",
                            fallback,
                            rootMessage(error)
                    );

                    /*
                     * IMPORTANT:
                     *
                     * If fallback is enabled, Beta MUST be attempted.
                     */
                    if (fallback) {

                        log.warn(
                                "PRIMARY ALPHA FAILED -> FALLING BACK TO BETA | model={}",
                                route.getFallbackModel()
                        );

                        return callProviderWithRetry(
                                betaProvider,
                                withModel(
                                        request,
                                        route.getFallbackModel()
                                ),
                                "beta"
                        )
                                .map(response ->
                                        createGatewayResponse(
                                                response,
                                                "beta",
                                                true
                                        )
                                )
                                .doOnError(betaError ->
                                        log.error(
                                                "Beta fallback also failed | error={}",
                                                rootMessage(betaError)
                                        )
                                );
                    }

                    return Mono.error(error);
                });
    }

    // =========================================================
    // PROVIDER CALL + RETRY
    // =========================================================

    private Mono<ChatCompletionResponse> callProviderWithRetry(
            LlmProvider provider,
            ChatCompletionRequest request,
            String providerName
    ) {
        log.info(
                "Calling provider | provider={} | model={}",
                providerName,
                request.getModel()
        );

        return provider
                .complete(request)

                .doOnSuccess(response ->
                        log.info(
                                "Provider success | provider={} | model={}",
                                providerName,
                                response != null
                                        ? response.getModel()
                                        : "null"
                        )
                )

                .doOnError(error ->
                        log.warn(
                                "Provider attempt failed | provider={} | model={} | error={}",
                                providerName,
                                request.getModel(),
                                rootMessage(error)
                        )
                )

                .retryWhen(
                        Retry.fixedDelay(
                                        MAX_RETRIES,
                                        RETRY_DELAY
                                )
                                .doBeforeRetry(signal ->
                                        log.warn(
                                                "Retrying provider | provider={} | model={} | retry={}/{} | error={}",
                                                providerName,
                                                request.getModel(),
                                                signal.totalRetries() + 1,
                                                MAX_RETRIES,
                                                rootMessage(signal.failure())
                                        )
                                )

                                // IMPORTANT:
                                // After retries are exhausted, propagate the
                                // ORIGINAL provider exception instead of wrapping
                                // it inside RetryExhaustedException.
                                .onRetryExhaustedThrow(
                                        (spec, signal) -> signal.failure()
                                )
                );
    }

    // =========================================================
    // CREATE GATEWAY RESPONSE
    // =========================================================

    private GatewayResponse createGatewayResponse(
            ChatCompletionResponse response,
            String provider,
            boolean fallbackUsed
    ) {

        double cost = 0.0;

        if (response != null && response.getUsage() != null) {

            cost =
                    costService.calculateCost(
                            response.getModel(),
                            response.getUsage()
                    );
        }

        log.info(
                "Gateway response | provider={} | fallbackUsed={} | model={} | cost={}",
                provider,
                fallbackUsed,
                response != null
                        ? response.getModel()
                        : "null",
                cost
        );

        return new GatewayResponse(
                response,
                provider,
                fallbackUsed,
                cost,
                false
        );
    }

    // =========================================================
    // MODEL ROUTING
    // =========================================================

    private ModelRoute resolveRoute(
            ChatCompletionRequest request
    ) {

        String requestedModel =
                request.getModel();

        if (requestedModel == null ||
                requestedModel.isBlank()) {

            throw new IllegalArgumentException(
                    "Model must be provided"
            );
        }

        // ---------------------------------------------------------
        // AUTO ROUTING
        // ---------------------------------------------------------

        if ("auto".equalsIgnoreCase(requestedModel)) {

            RoutingDecision decision =
                    autoRoutingService.route(request);

            if (decision == null) {

                throw new IllegalStateException(
                        "Auto routing returned no decision"
                );
            }

            String resolvedModel =
                    decision.getResolvedModel();

            if (resolvedModel == null ||
                    resolvedModel.isBlank()) {

                throw new IllegalStateException(
                        "Auto routing returned no resolved model"
                );
            }

            log.info(
                    "AUTO ROUTING | requestedModel=auto | resolvedModel={} | reason={} | confidence={}",
                    resolvedModel,
                    decision.getReason(),
                    decision.getConfidence()
            );

            return modelRouter.resolve(
                    withModel(
                            request,
                            resolvedModel
                    )
            );
        }

        // ---------------------------------------------------------
        // EXPLICIT MODEL
        // ---------------------------------------------------------

        return modelRouter.resolve(request);
    }

    // =========================================================
    // COPY REQUEST WITH SELECTED MODEL
    // =========================================================

    private ChatCompletionRequest withModel(
            ChatCompletionRequest request,
            String model
    ) {

        ChatCompletionRequest copy =
                new ChatCompletionRequest();

        copy.setModel(model);
        copy.setMessages(request.getMessages());
        copy.setStream(request.isStream());

        return copy;
    }

    // =========================================================
    // STREAMING
    // =========================================================

    public Mono<GatewayStreamResponse> stream(
            ChatCompletionRequest request,
            String provider,
            boolean fallback,
            VirtualKey virtualKey
    ) {

        budgetService.checkBudget(virtualKey);

        return streamWithFailover(
                request,
                provider,
                fallback,
                virtualKey
        );
    }

    // =========================================================
    // STREAM FAILOVER
    // =========================================================

    private Mono<GatewayStreamResponse> streamWithFailover(
            ChatCompletionRequest request,
            String provider,
            boolean fallback,
            VirtualKey virtualKey
    ) {

        ModelRoute route =
                resolveRoute(request);

        if ("alpha".equalsIgnoreCase(provider)) {

            return createAlphaStream(
                    request,
                    route,
                    virtualKey,
                    fallback
            );
        }

        if ("beta".equalsIgnoreCase(provider)) {

            return createBetaStream(
                    request,
                    route,
                    virtualKey,
                    fallback
            );
        }

        // Automatic provider selection
        return createAlphaStream(
                request,
                route,
                virtualKey,
                fallback
        );
    }

    // =========================================================
    // ALPHA STREAM
    // =========================================================

    private Mono<GatewayStreamResponse> createAlphaStream(
            ChatCompletionRequest request,
            ModelRoute route,
            VirtualKey virtualKey,
            boolean fallback
    ) {

        AtomicBoolean alphaEmitted =
                new AtomicBoolean(false);

        ChatCompletionRequest alphaRequest =
                withModel(
                        request,
                        route.getPrimaryModel()
                );

        log.info(
                "Starting Alpha stream | model={} | fallback={}",
                alphaRequest.getModel(),
                fallback
        );

        Flux<ChatCompletionChunk> alphaStream =
                alphaProvider
                        .stream(alphaRequest)

                        .doOnNext(chunk -> {

                            alphaEmitted.set(true);

                            addStreamUsage(
                                    chunk,
                                    virtualKey
                            );
                        })

                        .retryWhen(
                                Retry.fixedDelay(
                                                MAX_RETRIES,
                                                RETRY_DELAY
                                        )
                                        .doBeforeRetry(signal ->
                                                log.warn(
                                                        "Retrying Alpha stream | retry={}/{} | error={}",
                                                        signal.totalRetries() + 1,
                                                        MAX_RETRIES,
                                                        rootMessage(signal.failure())
                                                )
                                        )
                                        .filter(
                                                error ->
                                                        !alphaEmitted.get()
                                        )
                        );

        return alphaStream
                .collectList()

                .map(chunks ->
                        new GatewayStreamResponse(
                                Flux.fromIterable(chunks),
                                "alpha",
                                false
                        )
                )

                .onErrorResume(error -> {

                    log.error(
                            "Alpha streaming failed | fallback={} | error={}",
                            fallback,
                            rootMessage(error)
                    );

                    if (alphaEmitted.get()) {
                        return Mono.error(error);
                    }

                    if (!fallback) {
                        return Mono.error(error);
                    }

                    log.warn(
                            "ALPHA STREAM FAILED -> FALLING BACK TO BETA"
                    );

                    return createBetaStream(
                            request,
                            route,
                            virtualKey,
                            fallback
                    );
                });
    }

    // =========================================================
    // BETA STREAM
    // =========================================================

    private Mono<GatewayStreamResponse> createBetaStream(
            ChatCompletionRequest request,
            ModelRoute route,
            VirtualKey virtualKey,
            boolean fallback
    ) {

        AtomicBoolean betaEmitted =
                new AtomicBoolean(false);

        ChatCompletionRequest betaRequest =
                withModel(
                        request,
                        route.getFallbackModel()
                );

        log.info(
                "Starting Beta stream | model={} | fallback={}",
                betaRequest.getModel(),
                fallback
        );

        Flux<ChatCompletionChunk> betaStream =
                betaProvider
                        .stream(betaRequest)

                        .doOnNext(chunk -> {

                            betaEmitted.set(true);

                            addStreamUsage(
                                    chunk,
                                    virtualKey
                            );
                        })

                        .retryWhen(
                                Retry.fixedDelay(
                                                MAX_RETRIES,
                                                RETRY_DELAY
                                        )
                                        .doBeforeRetry(signal ->
                                                log.warn(
                                                        "Retrying Beta stream | retry={}/{} | error={}",
                                                        signal.totalRetries() + 1,
                                                        MAX_RETRIES,
                                                        rootMessage(signal.failure())
                                                )
                                        )
                                        .filter(
                                                error ->
                                                        !betaEmitted.get()
                                        )
                        );

        return betaStream
                .collectList()

                .map(chunks ->
                        new GatewayStreamResponse(
                                Flux.fromIterable(chunks),
                                "beta",
                                true
                        )
                )

                .onErrorResume(error -> {

                    log.error(
                            "Beta streaming failed | fallback={} | error={}",
                            fallback,
                            rootMessage(error)
                    );

                    if (betaEmitted.get()) {
                        return Mono.error(error);
                    }

                    if (!fallback) {
                        return Mono.error(error);
                    }

                    log.warn(
                            "BETA STREAM FAILED -> FALLING BACK TO ALPHA"
                    );

                    return createAlphaStream(
                            request,
                            route,
                            virtualKey,
                            fallback
                    );
                });
    }

    // =========================================================
    // STREAM USAGE + COST
    // =========================================================

    private void addStreamUsage(
            ChatCompletionChunk chunk,
            VirtualKey virtualKey
    ) {

        Usage usage =
                chunk.getUsage();

        if (usage == null) {
            return;
        }

        log.info(
                "Streaming tokens used: {}",
                usage.getTotalTokens()
        );

        rateLimitService.addTokens(
                virtualKey,
                usage.getTotalTokens()
        );

        double cost =
                costService.calculateCost(
                        chunk.getModel(),
                        usage
                );

        budgetService.addCost(
                virtualKey,
                cost
        );

        log.info(
                "Streaming request cost: ${}, monthly spent: ${}",
                cost,
                budgetService.getSpent(virtualKey)
        );
    }

    // =========================================================
    // ERROR MESSAGE
    // =========================================================

    private String rootMessage(
            Throwable error
    ) {

        if (error == null) {
            return "unknown";
        }

        Throwable current = error;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message =
                current.getMessage();

        if (message == null ||
                message.isBlank()) {

            return current.getClass()
                    .getSimpleName();
        }

        return message;
    }
}