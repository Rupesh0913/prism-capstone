package com.prism.gateway.provider;

import com.prism.gateway.model.ChatCompletionChunk;
import com.prism.gateway.model.ChatCompletionRequest;
import com.prism.gateway.model.ChatCompletionResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LlmProvider {

    Mono<ChatCompletionResponse> complete(ChatCompletionRequest request);

    Flux<ChatCompletionChunk> stream(ChatCompletionRequest request);
}
