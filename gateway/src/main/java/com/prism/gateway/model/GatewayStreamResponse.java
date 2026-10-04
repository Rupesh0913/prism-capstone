package com.prism.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Flux;

@AllArgsConstructor
@Getter
public class GatewayStreamResponse {

    private final Flux<ChatCompletionChunk> stream;
    private final String provider;
    private final boolean fallback;

}
