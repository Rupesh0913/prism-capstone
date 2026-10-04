package com.prism.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ModelRoute {
    private final String alias;
    private final String primaryModel;
    private final String fallbackModel;
}
