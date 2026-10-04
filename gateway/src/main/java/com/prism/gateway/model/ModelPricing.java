package com.prism.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ModelPricing {

    @JsonProperty("input_per_1m")
    private double inputPer1m;

    @JsonProperty("output_per_1m")
    private double outputPer1m;
}
