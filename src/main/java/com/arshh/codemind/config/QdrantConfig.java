package com.arshh.codemind.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects CodeMind to the Qdrant vector database via gRPC.
 */
@Configuration
public class QdrantConfig {

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(CodeMindProperties properties) {
        CodeMindProperties.Qdrant q = properties.getQdrant();

        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                q.getHost(),
                q.getGrpcPort(),
                q.isUseTls()
        );

        if (q.getApiKey() != null && !q.getApiKey().isBlank()) {
            builder.withApiKey(q.getApiKey());
        }

        return new QdrantClient(builder.build());
    }
}
