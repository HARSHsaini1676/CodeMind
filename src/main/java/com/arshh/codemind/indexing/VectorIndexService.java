package com.arshh.codemind.indexing;

import com.arshh.codemind.config.CodeMindProperties;
import com.arshh.codemind.dto.CodeChunk;
import com.arshh.codemind.dto.SearchResult;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores and searches code chunks and their embedding vectors in the Qdrant vector database.
 */
@Service
public class VectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexService.class);

    private final QdrantClient qdrantClient;
    private final CodeMindProperties properties;

    public VectorIndexService(QdrantClient qdrantClient, CodeMindProperties properties) {
        this.qdrantClient = qdrantClient;
        this.properties = properties;
    }

    /**
     * Checks if the Qdrant collection exists; creates it with cosine distance if missing.
     */
    public void ensureCollectionExists(int dimension) {
        String collectionName = properties.getQdrant().getCollectionName();
        try {
            boolean exists = qdrantClient.collectionExistsAsync(collectionName).get();
            if (!exists) {
                log.info("Creating Qdrant collection [{}] with dimension {}", collectionName, dimension);
                Collections.VectorParams params = Collections.VectorParams.newBuilder()
                        .setSize(dimension)
                        .setDistance(Collections.Distance.Cosine)
                        .build();
                qdrantClient.createCollectionAsync(collectionName, params).get();
                log.info("Collection [{}] created successfully", collectionName);
            }
        } catch (Exception e) {
            log.error("Failed checking/creating Qdrant collection [{}]: {}", collectionName, e.getMessage());
            throw new RuntimeException("Qdrant connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Upserts code chunks and their embedding vectors into Qdrant.
     */
    public void upsertChunks(List<CodeChunk> chunks, List<float[]> embeddings) {
        if (chunks.isEmpty() || embeddings.isEmpty()) {
            return;
        }

        String collectionName = properties.getQdrant().getCollectionName();
        List<PointStruct> points = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            CodeChunk chunk = chunks.get(i);
            float[] vector = embeddings.get(i);

            PointStruct point = PointStruct.newBuilder()
                    .setId(PointIdFactory.id(UUID.fromString(chunk.chunkId())))
                    .setVectors(VectorsFactory.vectors(vector))
                    .putPayload("chunkId", ValueFactory.value(chunk.chunkId()))
                    .putPayload("repositoryId", ValueFactory.value(chunk.repositoryId()))
                    .putPayload("commitSha", ValueFactory.value(chunk.commitSha()))
                    .putPayload("path", ValueFactory.value(chunk.path()))
                    .putPayload("language", ValueFactory.value(chunk.language()))
                    .putPayload("chunkType", ValueFactory.value(chunk.chunkType().name()))
                    .putPayload("symbolName", ValueFactory.value(chunk.symbolName() != null ? chunk.symbolName() : ""))
                    .putPayload("startLine", ValueFactory.value(chunk.startLine()))
                    .putPayload("endLine", ValueFactory.value(chunk.endLine()))
                    .putPayload("content", ValueFactory.value(chunk.content()))
                    .build();

            points.add(point);
        }

        try {
            log.info("Upserting {} points into Qdrant collection [{}]", points.size(), collectionName);
            qdrantClient.upsertAsync(collectionName, points).get();
            log.info("Successfully saved {} chunks in Qdrant", points.size());
        } catch (Exception e) {
            log.error("Failed to upsert points into Qdrant: {}", e.getMessage());
            throw new RuntimeException("Qdrant upsert error: " + e.getMessage(), e);
        }
    }

    /**
     * Searches Qdrant for chunks closest to the query vector, filtered by repositoryId.
     */
    public List<SearchResult> search(String repositoryId, float[] queryVector, int topK) {
        String collectionName = properties.getQdrant().getCollectionName();

        // Convert float[] to List<Float> for gRPC protobuf
        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float f : queryVector) {
            vectorList.add(f);
        }

        // Filter search specifically to the target repository
        Points.Filter filter = Points.Filter.newBuilder()
                .addMust(ConditionFactory.matchKeyword("repositoryId", repositoryId))
                .build();

        Points.SearchPoints searchPoints = Points.SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(vectorList)
                .setFilter(filter)
                .setLimit(topK)
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .build();

        try {
            List<Points.ScoredPoint> scoredPoints = qdrantClient.searchAsync(searchPoints).get();
            List<SearchResult> results = new ArrayList<>(scoredPoints.size());

            for (Points.ScoredPoint point : scoredPoints) {
                var payload = point.getPayloadMap();
                String chunkId = payload.containsKey("chunkId") ? payload.get("chunkId").getStringValue() : "";
                String path = payload.containsKey("path") ? payload.get("path").getStringValue() : "";
                int startLine = payload.containsKey("startLine") ? (int) payload.get("startLine").getIntegerValue() : 0;
                int endLine = payload.containsKey("endLine") ? (int) payload.get("endLine").getIntegerValue() : 0;
                String symbolName = payload.containsKey("symbolName") ? payload.get("symbolName").getStringValue() : "";
                String content = payload.containsKey("content") ? payload.get("content").getStringValue() : "";

                results.add(new SearchResult(
                        chunkId,
                        path,
                        startLine,
                        endLine,
                        symbolName,
                        point.getScore(),
                        content
                ));
            }

            return results;
        } catch (Exception e) {
            log.error("Failed to execute search on Qdrant: {}", e.getMessage(), e);
            throw new RuntimeException("Qdrant search error: " + e.getMessage(), e);
        }
    }

    /**
     * Searches Qdrant for code chunks where symbolName matches the given identifier directly.
     */
    public List<SearchResult> searchBySymbol(String repositoryId, String symbol, int limit) {
        return searchByFilter(repositoryId, ConditionFactory.matchKeyword("symbolName", symbol), limit);
    }

    /**
     * Searches Qdrant for code chunks whose file path ends with or matches a specific path/filename.
     */
    public List<SearchResult> searchByPath(String repositoryId, String pathKeyword, int limit) {
        return searchByFilter(repositoryId, ConditionFactory.matchText("path", pathKeyword), limit);
    }

    private List<SearchResult> searchByFilter(String repositoryId, Points.Condition condition, int limit) {
        String collectionName = properties.getQdrant().getCollectionName();

        Points.Filter filter = Points.Filter.newBuilder()
                .addMust(ConditionFactory.matchKeyword("repositoryId", repositoryId))
                .addMust(condition)
                .build();

        Points.ScrollPoints scrollPoints = Points.ScrollPoints.newBuilder()
                .setCollectionName(collectionName)
                .setFilter(filter)
                .setLimit(limit)
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .build();

        try {
            Points.ScrollResponse response = qdrantClient.scrollAsync(scrollPoints).get();
            List<SearchResult> results = new ArrayList<>();

            for (Points.RetrievedPoint point : response.getResultList()) {
                var payload = point.getPayloadMap();
                String chunkId = payload.containsKey("chunkId") ? payload.get("chunkId").getStringValue() : "";
                String path = payload.containsKey("path") ? payload.get("path").getStringValue() : "";
                int startLine = payload.containsKey("startLine") ? (int) payload.get("startLine").getIntegerValue() : 0;
                int endLine = payload.containsKey("endLine") ? (int) payload.get("endLine").getIntegerValue() : 0;
                String symbolName = payload.containsKey("symbolName") ? payload.get("symbolName").getStringValue() : "";
                String content = payload.containsKey("content") ? payload.get("content").getStringValue() : "";

                results.add(new SearchResult(
                        chunkId,
                        path,
                        startLine,
                        endLine,
                        symbolName,
                        1.0f,
                        content
                ));
            }

            return results;
        } catch (Exception e) {
            log.warn("Payload search failed for condition: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
