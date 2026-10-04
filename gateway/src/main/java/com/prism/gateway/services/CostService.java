package com.prism.gateway.services;

import com.prism.gateway.model.ModelPricing;
import com.prism.gateway.model.Usage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CostService {
    private final Map<String, ModelPricing> pricing = new HashMap<>();

    public CostService(ObjectMapper objectMapper) {

        try {
            InputStream inputStream = getClass()
                            .getClassLoader()
                            .getResourceAsStream("model_pricing.json");

            JsonNode root = objectMapper.readTree(inputStream);
            for (Map.Entry<String, JsonNode> entry : root.properties()) {

                String model = entry.getKey();

                if (model.equals("_comment")) {
                    continue;
                }
                ModelPricing modelPricing = objectMapper.treeToValue(entry.getValue(), ModelPricing.class);
                pricing.put(model, modelPricing);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load model pricing", e);
        }
    }

    public double calculateCost(String model, Usage usage) {

        if (usage == null) {
            return 0;
        }

        ModelPricing modelPricing = pricing.get(model);

        if (modelPricing == null) {
            throw new IllegalArgumentException(
                    "No pricing found for model: " + model
            );
        }

        double inputCost = usage.getPromptTokens()
                        * modelPricing.getInputPer1m()
                        / 1_000_000;

        double outputCost = usage.getCompletionTokens()
                        * modelPricing.getOutputPer1m()
                        / 1_000_000;

        return inputCost + outputCost;
    }

    public void test() {

        Usage usage = new Usage();

        usage.setPromptTokens(9);
        usage.setCompletionTokens(26);
        usage.setTotalTokens(35);

        double cost = calculateCost("alpha-small", usage);

        System.out.println("Calculated cost = " + cost);
    }

}
