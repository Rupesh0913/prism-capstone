package com.prism.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class GatewayResponse {

    private final ChatCompletionResponse response;
    private final String provider;
    private final boolean fallback;
    private double cost;
    private final boolean cacheHit;
}
