package com.prism.gateway;

import com.prism.gateway.model.ChatCompletionRequest;
import com.prism.gateway.services.Difficulty;
import com.prism.gateway.services.DifficultyClassifier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingEvaluationTest {

    private final DifficultyClassifier classifier = new DifficultyClassifier();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluateRouting() throws Exception {

        InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream("routing_eval.jsonl");

        assertTrue(inputStream != null, "routing_eval.jsonl not found");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        );

        String line;

        int total = 0;
        int correct = 0;

        while ((line = reader.readLine()) != null) {

            if (line.isBlank()) {
                continue;
            }

            RoutingCase testCase =
                    objectMapper.readValue(line, RoutingCase.class);

            ChatCompletionRequest request =
                    createRequest(testCase.prompt);

            Difficulty actual =
                    classifier.classify(request);

            boolean isCorrect =
                    actual.name().equalsIgnoreCase(testCase.expected_tier);

            total++;

            if (isCorrect) {
                correct++;
            }

            System.out.println(
                    testCase.id
                            + " | expected=" + testCase.expected_tier
                            + " | actual=" + actual
                            + " | " + (isCorrect ? "PASS" : "FAIL")
            );
        }

        double accuracy =
                (double) correct / total * 100;

        System.out.println();
        System.out.println("=================================");
        System.out.println("Routing Evaluation");
        System.out.println("=================================");
        System.out.println("Correct : " + correct);
        System.out.println("Total   : " + total);
        System.out.println("Accuracy: " + accuracy + "%");
        System.out.println("=================================");

        reader.close();

        assertTrue(total > 0, "No routing cases found");
    }

    private ChatCompletionRequest createRequest(String prompt) {

        ChatCompletionRequest request =
                new ChatCompletionRequest();

        ChatCompletionRequest.Message message =
                new ChatCompletionRequest.Message();

        message.setRole("user");
        message.setContent(prompt);

        request.setMessages(
                new ChatCompletionRequest.Message[]{message}
        );

        return request;
    }

    static class RoutingCase {

        public String id;

        public String expected_tier;

        public String note;

        public String prompt;
    }
}