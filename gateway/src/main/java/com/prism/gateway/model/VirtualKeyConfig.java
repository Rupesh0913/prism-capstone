package com.prism.gateway.model;

import lombok.Data;

import java.util.List;

@Data
public class VirtualKeyConfig {

    private String _comment;
    private List<VirtualKey> tenants;
}
