package com.prism.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CacheEntry {

    private String id;
    private String query;
    private ChatCompletionResponse response;
}
