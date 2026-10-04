package com.prism.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RoutingDecision {

    private final String requestedModel;
    private final String resolvedModel;
    private final String reason;
    private final double confidence;
}