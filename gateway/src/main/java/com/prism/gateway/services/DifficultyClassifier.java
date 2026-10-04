package com.prism.gateway.services;

import com.prism.gateway.model.ChatCompletionRequest;
import org.springframework.stereotype.Service;

@Service
public class DifficultyClassifier {

    public Difficulty classify(ChatCompletionRequest request) {
        String prompt = getUserPrompt(request);

        String text = prompt.toLowerCase();

        if (isComplex(text)) {
            return Difficulty.SMART;
        }

        return Difficulty.FAST;
    }

    private String getUserPrompt(ChatCompletionRequest request) {

        if (request.getMessages() == null) {
            return "";
        }

        for (ChatCompletionRequest.Message message : request.getMessages()) {

            if ("user".equalsIgnoreCase(message.getRole())) {
                return message.getContent();
            }
        }

        return "";
    }

    private boolean isComplex(String text) {

        String[] complexWords = {
                "design",
                "architecture",
                "distributed",
                "concurrent",
                "scalable",
                "fault tolerant",
                "optimize",
                "optimization",
                "prove",
                "analyze",
                "compare",
                "trade-off",
                "debug",
                "race condition",
                "algorithm",
                "system design",
                "estimate",
                "reasoning",
                "guarantees",
                "network partition",
                "migration",
                "roll back",
                "rollback",
                "plan",
                "planning",
                "causes",
                "confirm each",
                "likelihood",
                "diagnose",
                "diagnosis"
        };

        for (String word : complexWords) {

            if (text.contains(word)) {
                return true;
            }
        }

        return false;
    }
}
