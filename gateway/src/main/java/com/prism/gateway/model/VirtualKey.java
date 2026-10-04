package com.prism.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class VirtualKey {

    private String team;

    @JsonProperty("virtual_key")
    private String virtualKey;

    @JsonProperty("monthly_budget_usd")
    private double monthlyBudgetUsd;

    @JsonProperty("rate_limit")
    private RateLimit rateLimit;

    @JsonProperty("model_allowlist")
    private List<String> modelAllowlist;

    @JsonProperty("semantic_cache")
    private SemanticCache semanticCache;

    @Data
    public static class RateLimit {

        @JsonProperty("requests_per_minute")
        private int requestsPerMinute;

        @JsonProperty("tokens_per_minute")
        private int tokensPerMinute;
    }

    @Data
    public static class SemanticCache {

        private boolean enabled;

        @JsonProperty("similarity_threshold")
        private Double similarityThreshold;
    }
}
