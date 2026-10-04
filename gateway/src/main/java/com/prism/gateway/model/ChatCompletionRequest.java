package com.prism.gateway.model;

import lombok.Data;

@Data
public class ChatCompletionRequest {

    private String model;
    private Message[] messages;
    private boolean stream;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}


