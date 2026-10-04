package com.prism.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.DefaultEmbeddingFunction;
import tech.amikos.chromadb.EFException;
import tech.amikos.chromadb.handler.ApiException;

@Configuration
public class ChromaConfig {

    @Bean
    public Client chromaClient(
            @Value("${prism.chroma.url}") String chromaUrl
    ) {
        return new Client(chromaUrl);
    }

    @Bean
    public DefaultEmbeddingFunction embeddingFunction() throws EFException {
        return new DefaultEmbeddingFunction();
    }

    @Bean
    public Collection semanticCacheCollection(
            Client chromaClient,
            DefaultEmbeddingFunction embeddingFunction
    ) throws ApiException {

        return chromaClient.createCollection(
                "prism-semantic-cache",
                null,
                true,
                embeddingFunction
        );
    }
}