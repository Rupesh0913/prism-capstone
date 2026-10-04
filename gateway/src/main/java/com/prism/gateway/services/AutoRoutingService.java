package com.prism.gateway.services;

import com.prism.gateway.model.ChatCompletionRequest;
import com.prism.gateway.model.RoutingDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.amikos.chromadb.EFException;
import tech.amikos.chromadb.Embedding;
import tech.amikos.chromadb.embeddings.DefaultEmbeddingFunction;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AutoRoutingService {

    private final DefaultEmbeddingFunction embeddingFunction;

    /*
     * These are semantic prototypes.
     *
     * They represent the type of requests that normally belong
     * to the FAST and SMART routing categories.
     */
    private static final List<String> FAST_PROTOTYPES = List.of(
            "What is the capital of France?",
            "What is the definition of polymorphism?",
            "Who was the first president of the United States?",
            "Convert 10 kilometers to miles.",
            "Summarize this short paragraph.",
            "What does this Java method do?",
            "Give me a simple explanation of this concept.",
            "What is the syntax for a Java loop?",
            "What is the difference between a class and an object?",
            "Translate this sentence into Hindi."
    );

    private static final List<String> SMART_PROTOTYPES = List.of(
            "Design a scalable distributed system for millions of users.",
            "Analyze this complex software architecture and identify bottlenecks.",
            "Debug this complicated concurrency issue and explain the root cause.",
            "Design a highly available payment processing architecture.",
            "Compare multiple database architectures and explain their tradeoffs.",
            "Analyze this algorithm and determine its complexity and optimization strategy.",
            "Develop a detailed system design with fault tolerance and consistency guarantees.",
            "Reason through this complex business problem and propose multiple solutions.",
            "Analyze a large codebase and determine the architectural problems.",
            "Design a distributed system capable of handling very high traffic."
    );

    private static final double FAST_THRESHOLD = 0.60;
    private static final double SMART_THRESHOLD = 0.60;

    public RoutingDecision route(ChatCompletionRequest request) {

        if (request == null) {
            return new RoutingDecision(
                    "auto",
                    "fast",
                    "Empty request",
                    1.0
            );
        }

        String query = buildQuery(request);

        if (query.isBlank()) {
            return new RoutingDecision(
                    "auto",
                    "fast",
                    "Empty prompt",
                    1.0
            );
        }

        try {

            double fastScore = calculateCategoryScore(
                    query,
                    FAST_PROTOTYPES
            );

            double smartScore = calculateCategoryScore(
                    query,
                    SMART_PROTOTYPES
            );

            if (smartScore >= fastScore) {

                return new RoutingDecision(
                        "auto",
                        "smart",
                        "High semantic similarity to complex reasoning tasks",
                        smartScore
                );
            }

            return new RoutingDecision(
                    "auto",
                    "fast",
                    "High semantic similarity to simple/factual tasks",
                    fastScore
            );

        } catch (Exception e) {

            /*
             * Auto routing must never bring down the gateway.
             *
             * If NLP/embedding fails, fall back to FAST.
             */
            return new RoutingDecision(
                    "auto",
                    "fast",
                    "NLP routing unavailable; fallback to fast",
                    0.0
            );
        }
    }

    private double calculateCategoryScore(String query, List<String> prototypes)throws EFException {

        List<Embedding> queryEmbedding = embeddingFunction.embedDocuments(List.of(query));

        List<Embedding> prototypeEmbeddings = embeddingFunction.embedDocuments(prototypes);

        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
            return 0.0;
        }

        float[] queryVector = queryEmbedding.get(0).asArray();

        double bestScore = 0.0;

        for (Embedding prototype : prototypeEmbeddings) {
            double similarity = cosineSimilarity(queryVector, prototype.asArray());
            bestScore = Math.max(bestScore, similarity);
        }
        return bestScore;
    }

    private double cosineSimilarity(float[] first, float[] second) {
        if (first == null || second == null || first.length != second.length) {
            return 0.0;
        }

        double dot = 0.0, magA = 0.0, magB = 0.0;
        for (int i = 0; i < first.length; i++) {
            dot += first[i] * second[i];
            magA += first[i] * first[i];
            magB += second[i] * second[i];
        }

        if (magA == 0.0 || magB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }

    private String buildQuery(
            ChatCompletionRequest request
    ) {

        if (request.getMessages() == null) {
            return "";
        }

        StringBuilder query =
                new StringBuilder();

        for (ChatCompletionRequest.Message message :
                request.getMessages()) {

            if (message.getContent() == null) {
                continue;
            }

            query.append(message.getRole())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }

        return query.toString().trim();
    }
}