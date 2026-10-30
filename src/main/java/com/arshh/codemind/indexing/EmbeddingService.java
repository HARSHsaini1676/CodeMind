package com.arshh.codemind.indexing;

import com.arshh.codemind.config.CodeMindProperties;
import com.arshh.codemind.dto.CodeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates vector embeddings for code snippets using Spring AI.
 * Batches requests in chunks of 32 to avoid HTTP timeouts and token limits.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final CodeMindProperties properties;

    public EmbeddingService(EmbeddingModel embeddingModel, CodeMindProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /**
     * Converts a single text string (such as a search question) into a vector.
     */
    public float[] embedQuery(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * Converts code chunks into vector embeddings in batches.
     */
    public List<float[]> embedChunks(List<CodeChunk> chunks) {
        List<float[]> allVectors = new ArrayList<>(chunks.size());
        int batchSize = properties.getEmbeddingBatchSize();

        log.info("Generating embeddings for {} chunks (batch size: {})", chunks.size(), batchSize);

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<CodeChunk> batch = chunks.subList(i, end);

            // Prepare text strings for embedding model
            List<String> texts = batch.stream()
                    .map(CodeChunk::content)
                    .toList();

            List<float[]> batchVectors = embeddingModel.embed(texts);
            allVectors.addAll(batchVectors);

            log.info("Embedded chunks {} to {} of {}", i + 1, end, chunks.size());
        }

        return allVectors;
    }

    /**
     * Returns the vector dimension size (e.g. 1536 for text-embedding-3-small).
     */
    public int getDimensions() {
        return embeddingModel.dimensions();
    }
}
