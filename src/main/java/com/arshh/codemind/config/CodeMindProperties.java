package com.arshh.codemind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Global configuration properties for repository ingestion, parsing, vector storage, and reranking.
 */
@Configuration
@ConfigurationProperties(prefix = "codemind")
public class CodeMindProperties {

    private String workspaceDir = "./workspaces";
    private int cloneTimeoutSeconds = 60;
    private int maxFileSizeKb = 500;
    private int maxFilesPerRepo = 1500;
    private int embeddingBatchSize = 32;
    private int defaultTopK = 8;
    private Qdrant qdrant = new Qdrant();
    private Rerank rerank = new Rerank();

    public String getWorkspaceDir() { return workspaceDir; }
    public void setWorkspaceDir(String workspaceDir) { this.workspaceDir = workspaceDir; }

    public int getCloneTimeoutSeconds() { return cloneTimeoutSeconds; }
    public void setCloneTimeoutSeconds(int cloneTimeoutSeconds) { this.cloneTimeoutSeconds = cloneTimeoutSeconds; }

    public int getMaxFileSizeKb() { return maxFileSizeKb; }
    public void setMaxFileSizeKb(int maxFileSizeKb) { this.maxFileSizeKb = maxFileSizeKb; }

    public int getMaxFilesPerRepo() { return maxFilesPerRepo; }
    public void setMaxFilesPerRepo(int maxFilesPerRepo) { this.maxFilesPerRepo = maxFilesPerRepo; }

    public int getEmbeddingBatchSize() { return embeddingBatchSize; }
    public void setEmbeddingBatchSize(int embeddingBatchSize) { this.embeddingBatchSize = embeddingBatchSize; }

    public int getDefaultTopK() { return defaultTopK; }
    public void setDefaultTopK(int defaultTopK) { this.defaultTopK = defaultTopK; }

    public Qdrant getQdrant() { return qdrant; }
    public void setQdrant(Qdrant qdrant) { this.qdrant = qdrant; }

    public Rerank getRerank() { return rerank; }
    public void setRerank(Rerank rerank) { this.rerank = rerank; }

    public static class Qdrant {
        private String host = "localhost";
        private int port = 6334;
        private int grpcPort = 6334;
        private int httpPort = 6333;
        private String apiKey = "";
        private String collectionName = "codemind-code-chunks";
        private boolean useTls = false;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public int getGrpcPort() { return grpcPort; }
        public void setGrpcPort(int grpcPort) { this.grpcPort = grpcPort; }

        public int getHttpPort() { return httpPort; }
        public void setHttpPort(int httpPort) { this.httpPort = httpPort; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getCollectionName() { return collectionName; }
        public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

        public boolean isUseTls() { return useTls; }
        public void setUseTls(boolean useTls) { this.useTls = useTls; }
    }

    public static class Rerank {
        private boolean enabled = true;
        private String apiKey = "";
        private String baseUrl = "https://api.jina.ai/v1/rerank";
        private String model = "jina-reranker-v2-base-multilingual";
        private int candidateMultiplier = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getCandidateMultiplier() { return candidateMultiplier; }
        public void setCandidateMultiplier(int candidateMultiplier) { this.candidateMultiplier = candidateMultiplier; }
    }
}
