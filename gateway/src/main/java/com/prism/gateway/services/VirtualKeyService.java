package com.prism.gateway.services;

import com.prism.gateway.exception.AuthenticationException;
import com.prism.gateway.exception.ModelNotAllowedException;
import com.prism.gateway.model.VirtualKey;
import com.prism.gateway.model.VirtualKeyConfig;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class VirtualKeyService {

    private final VirtualKeyConfig config;

    public VirtualKeyService(ObjectMapper objectMapper) {

        try {

            ClassPathResource resource = new ClassPathResource("seed_keys.json");
            config = objectMapper.readValue(resource.getInputStream(), VirtualKeyConfig.class);

        } catch (IOException e) {

            throw new RuntimeException("Failed to load seed_keys.json", e);
        }
    }

    public VirtualKey authenticate(String authorizationHeader) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {

            throw new AuthenticationException("Missing bearer token");
        }

        String keyValue = authorizationHeader.substring("Bearer ".length());

        for (VirtualKey key : config.getTenants()) {

            if (key.getVirtualKey().equals(keyValue)) {
                return key;
            }
        }

        throw new AuthenticationException("Invalid API key");
    }

    public void validateModel(VirtualKey key, String requestedModel) {

        if (!key.getModelAllowlist().contains(requestedModel)) {
            throw new ModelNotAllowedException("Model '" + requestedModel + "' is not allowed for team '" + key.getTeam() + "'");
        }
    }
}