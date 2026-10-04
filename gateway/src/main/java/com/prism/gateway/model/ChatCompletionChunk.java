package com.prism.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatCompletionChunk {

    private String id;

    private String object;

    private long created;

    private String model;

    private Choice[] choices;

    private Usage usage;

    @Data
    public static class Choice{
        private int index;

        private Delta delta;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Delta{
        private String role;
        private String content;
    }
}
