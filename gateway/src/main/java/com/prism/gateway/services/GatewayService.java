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
                "Checking semantic cache | virtualKey={} | model={}",
                virtualKey.getVirtualKey(),
                request.getModel()
        );

        ChatCompletionResponse cached =
                semanticCacheService.get(
                        request,
                        virtualKey
                );

        // =====================================================
        // CACHE HIT
        // =====================================================

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

        // =====================================================
        // CACHE MISS
        // =====================================================

        log.info(
                "SEMANTIC CACHE MISS | virtualKey={} | model={}",
                virtualKey.getVirtualKey(),
                request.getModel()
        );

        return callWithFailover(
                request,
                provider,
                fallback
        ).doOnNext(result -> {

            ChatCompletionResponse response =
                    result.getResponse();

            Usage usage =
                    response.getUsage();

            // =================================================
            // TOKEN + COST ACCOUNTING
            // =================================================

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

            // =================================================
            // SEMANTIC CACHE WRITE
            // =================================================

            log.info(
                    "Storing response in semantic cache | " +
                            "virtualKey={} | model={}",
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
    // NORMAL REQUEST + PROVIDER FAILOVER
    // =========================================================

    private Mono<GatewayResponse> callWithFailover(
            ChatCompletionRequest request,
            String provider,
            boolean fallback
    ) {

        /*
         * Resolve the model first.
         *
         * If request.model == "auto":
         *
         *      AutoRoutingService
         *              ↓
         *        fast / smart
         *              ↓
         *        ModelRouter
         *              ↓
         *    actual provider models
         *
         * For explicit fast/smart requests, the existing
         * ModelRouter behavior is preserved.
         */
        ModelRoute route =
                resolveRoute(request);

        // =====================================================
        // EXPLICIT PROVIDER
        // =====================================================

        if (provider != null && !provider.isBlank()) {

            // =================================================
            // ALPHA
            // =================================================

            if ("alpha".equalsIgnoreCase(provider)) {

                return retry(
                        alphaProvider.complete(
                                withModel(
                                        request,
                                        route.getPrimaryModel()
                                )
                        )
                ).map(response -> {

                    double cost =
                            costService.calculateCost(
                                    response.getModel(),
                                    response.getUsage()
                            );

                    return new GatewayResponse(
                            response,
                            "alpha",
                            false,
                            cost,
                            false
                    );

                }).onErrorResume(error -> {

                    if (!fallback) {
                        return Mono.error(error);
                    }

                    log.info(
                            "Alpha failed. Falling back to Beta."
                    );

                    return retry(
                            betaProvider.complete(
                                    withModel(
                                            request,
                                            route.getFallbackModel()
                                    )
                            )
                    ).map(response -> {

                        double cost =
                                costService.calculateCost(
                                        response.getModel(),
                                        response.getUsage()
                                );

                        return new GatewayResponse(
                                response,
                                "beta",
                                true,
                                cost,
                                false
                        );
                    });
                });
            }


            // =================================================
            // BETA
            // =================================================

            if ("beta".equalsIgnoreCase(provider)) {

                return retry(
                        betaProvider.complete(
                                withModel(
                                        request,
                                        route.getFallbackModel()
                                )
                        )
                ).map(response -> {

                    double cost =
                            costService.calculateCost(
                                    response.getModel(),
                                    response.getUsage()
                            );

                    return new GatewayResponse(
                            response,
                            "beta",
                            false,
                            cost,
                            false
                    );

                }).onErrorResume(error -> {

                    if (!fallback) {
                        return Mono.error(error);
                    }

                    log.info(
                            "Beta failed. Falling back to Alpha."
                    );

                    return retry(
                            alphaProvider.complete(
                                    withModel(
                                            request,
                                            route.getPrimaryModel()
                                    )
                            )
                    ).map(response -> {

                        double cost =
                                costService.calculateCost(
                                        response.getModel(),
                                        response.getUsage()
                                );

                        return new GatewayResponse(
                                response,
                                "alpha",
                                true,
                                cost,
                                false
                        );
                    });
                });
            }


            // =================================================
            // UNKNOWN PROVIDER
            // =================================================

            return Mono.error(
                    new IllegalArgumentException(
                            "Unknown provider: " + provider
                    )
            );
        }


        // =====================================================
        // AUTOMATIC PROVIDER SELECTION
        // =====================================================

        log.info(
                "Automatic provider selection | primary={} | fallback={}",
                route.getPrimaryModel(),
                route.getFallbackModel()
        );

        return retry(
                alphaProvider.complete(
                        withModel(
                                request,
                                route.getPrimaryModel()
                        )
                )
        ).map(response -> {

            double cost =
                    costService.calculateCost(
                            response.getModel(),
                            response.getUsage()
                    );

            return new GatewayResponse(
                    response,
                    "alpha",
                    false,
                    cost,
                    false
            );

        }).onErrorResume(error -> {

            if (!fallback) {
                return Mono.error(error);
            }

            log.info(
                    "Primary provider failed. Falling back to Beta."
            );

            return retry(
                    betaProvider.complete(
                            withModel(
                                    request,
                                    route.getFallbackModel()
                            )
                    )
            ).map(response -> {

                double cost =
                        costService.calculateCost(
                                response.getModel(),
                                response.getUsage()
                        );

                return new GatewayResponse(
                        response,
                        "beta",
                        true,
                        cost,
                        false
                );
            });
        });
    }


    // =========================================================
    // MODEL ROUTING
    // =========================================================

    private ModelRoute resolveRoute(
            ChatCompletionRequest request
    ) {

        String requestedModel =
                request.getModel();

        // -----------------------------------------------------
        // Explicit model
        // -----------------------------------------------------

        if (requestedModel == null ||
                requestedModel.isBlank()) {

            throw new IllegalArgumentException(
                    "Model must be provided"
            );
        }

        // -----------------------------------------------------
        // NLP / EMBEDDING BASED AUTO ROUTING
        // -----------------------------------------------------

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
                    "AUTO ROUTING | requestedModel={} | " +
                            "resolvedModel={} | reason={} | confidence={}",
                    decision.getRequestedModel(),
                    decision.getResolvedModel(),
                    decision.getReason(),
                    decision.getConfidence()
            );

            /*
             * Convert:
             *
             *      auto
             *       ↓
             *      fast / smart
             *
             * Then let ModelRouter convert:
             *
             *      fast
             *       ↓
             * alpha-small / beta-small
             *
             *      smart
             *       ↓
             * alpha-large / beta-large
             */
            ChatCompletionRequest routedRequest =
                    withModel(
                            request,
                            resolvedModel
                    );

            return modelRouter.resolve(
                    routedRequest
            );
        }

        // -----------------------------------------------------
        // Explicit fast / smart
        // -----------------------------------------------------

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
    // STREAMING REQUEST
    // =========================================================

    public Mono<GatewayStreamResponse> stream(
            ChatCompletionRequest request,
            String provider,
            boolean fallback,
            VirtualKey virtualKey
    ) {

        budgetService.checkBudget(
                virtualKey
        );

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

        /*
         * IMPORTANT:
         *
         * The exact same NLP auto-routing logic is used
         * for streaming requests.
         */
        ModelRoute route =
                resolveRoute(request);


        // =====================================================
        // EXPLICIT ALPHA
        // =====================================================

        if ("alpha".equalsIgnoreCase(provider)) {

            return createAlphaStream(
                    request,
                    route,
                    virtualKey,
                    fallback
            );
        }


        // =====================================================
        // EXPLICIT BETA
        // =====================================================

        if ("beta".equalsIgnoreCase(provider)) {

            return createBetaStream(
                    request,
                    route,
                    virtualKey,
                    fallback
            );
        }


        // =====================================================
        // AUTOMATIC PROVIDER SELECTION
        // =====================================================

        log.info(
                "Automatic streaming provider selection | " +
                        "primary={} | fallback={}",
                route.getPrimaryModel(),
                route.getFallbackModel()
        );

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
                                ).filter(
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

                    /*
                     * Alpha already emitted data.
                     *
                     * We cannot safely switch to Beta.
                     */
                    if (alphaEmitted.get()) {
                        return Mono.error(error);
                    }

                    /*
                     * No fallback requested.
                     */
                    if (!fallback) {
                        return Mono.error(error);
                    }

                    log.info(
                            "Alpha streaming failed before " +
                                    "emitting. Falling back to Beta."
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
                                ).filter(
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

                    /*
                     * Beta already emitted data.
                     *
                     * We cannot safely switch to Alpha.
                     */
                    if (betaEmitted.get()) {
                        return Mono.error(error);
                    }

                    /*
                     * No fallback requested.
                     */
                    if (!fallback) {
                        return Mono.error(error);
                    }

                    log.info(
                            "Beta streaming failed before " +
                                    "emitting. Falling back to Alpha."
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
    // STREAM USAGE + TOKEN ACCOUNTING
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
                "Streaming request cost: ${}, " +
                        "monthly spent: ${}",
                cost,
                budgetService.getSpent(virtualKey)
        );
    }


    // =========================================================
    // NORMAL REQUEST RETRY
    // =========================================================

    private <T> Mono<T> retry(
            Mono<T> request
    ) {

        return request.retryWhen(
                Retry.fixedDelay(
                        MAX_RETRIES,
                        RETRY_DELAY
                )
        );
    }
}