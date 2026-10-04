package com.prism.gateway.services;

import com.prism.gateway.model.ChatCompletionRequest;
import com.prism.gateway.model.ModelRoute;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ModelRouter {

    private final DifficultyClassifier difficultyClassifier;

    private final Map<String, ModelRoute> routes = Map.of("fast", new ModelRoute("fast", "alpha-small", "beta-small"),
                                                        "smart", new ModelRoute("smart", "alpha-large", "beta-large"));

    public ModelRouter(DifficultyClassifier difficultyClassifier) {
        this.difficultyClassifier = difficultyClassifier;
    }

    public ModelRoute resolve(ChatCompletionRequest request) {
        String alias = request.getModel();

        if ("auto".equalsIgnoreCase(alias)) {

            Difficulty difficulty =
                    difficultyClassifier.classify(request);

            if (difficulty == Difficulty.SMART) {
                alias = "smart";
            } else {
                alias = "fast";
            }
        }

        ModelRoute route = routes.get(alias);

        if (route == null) {
            throw new IllegalArgumentException(
                    "Unknown model alias: " + alias
            );
        }

        return route;

    }
}
