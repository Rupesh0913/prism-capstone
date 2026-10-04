package com.prism.gateway;

import com.prism.gateway.model.ChatCompletionRequest;
import com.prism.gateway.services.Difficulty;
import com.prism.gateway.services.DifficultyClassifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DifficultyClassifierTest {
    private final DifficultyClassifier classifier = new DifficultyClassifier();

    @Test
    void simpleQuestionShouldBeFast() {

        ChatCompletionRequest request = createRequest(
                "What is an API gateway?"
        );

        assertEquals(
                Difficulty.FAST,
                classifier.classify(request)
        );
    }

    @Test
    void architectureQuestionShouldBeSmart() {

        ChatCompletionRequest request = createRequest(
                "Design a distributed payment system."
        );

        assertEquals(
                Difficulty.SMART,
                classifier.classify(request)
        );
    }

    @Test
    void shortButComplexQuestionShouldBeSmart() {

        ChatCompletionRequest request = createRequest(
                "Design a fault tolerant distributed system."
        );

        assertEquals(
                Difficulty.SMART,
                classifier.classify(request)
        );
    }

    @Test
    void longButSimpleQuestionShouldBeFast() {

        ChatCompletionRequest request = createRequest(
                "Explain what an API gateway is. " +
                        "Please explain it in simple terms, " +
                        "give a short example, and describe its basic purpose " +
                        "for someone who is learning backend development."
        );

        assertEquals(
                Difficulty.FAST,
                classifier.classify(request)
        );
    }

    private ChatCompletionRequest createRequest(String content) {

        ChatCompletionRequest request = new ChatCompletionRequest();

        ChatCompletionRequest.Message message =
                new ChatCompletionRequest.Message();

        message.setRole("user");
        message.setContent(content);

        request.setMessages(
                new ChatCompletionRequest.Message[]{message}
        );

        return request;
    }
}
