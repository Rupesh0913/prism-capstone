package com.prism.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
@Data
public class ChatCompletionResponse {

    private String id;
    private String object;
    private long created;
    private String model;
    private Choice[] choices;
    private Usage usage;

    @Data
    public static class Choice{
        private int index;
        private Message message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }
    @Data
    public static class Message{
        private String role;
        private String content;
    }
}
