package com.arshh.codemind.retrieval;

import com.arshh.codemind.config.CodeMindProperties;
import com.arshh.codemind.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reranks code candidates using Jina AI's multilingual cross-encoder reranking API.
 * Falls back gracefully to original candidates if the API call fails or if no API key is provided.
 */
@Service
public class JinaRerankingService implements RerankingService {

    private static final Logger log = LoggerFactory.getLogger(JinaRerankingService.class);

    private final CodeMindProperties properties;
    private final RestClient restClient;

    public JinaRerankingService(CodeMindProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        CodeMindProperties.Rerank config = properties.getRerank();

        // If reranking is disabled or no API key is configured, preserve candidate order
        if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.debug("Reranker is skipped (disabled or no API key configured). Returning topK candidates directly.");
            return candidates.stream().limit(topK).toList();
        }

        try {
            // Prepare documents text list for Jina API
            List<String> documents = candidates.stream()
                    .map(c -> String.format("File: %s\nSymbol: %s\nCode:\n%s",
                            c.path(),
                            c.symbolName() != null ? c.symbolName() : "",
                            c.snippet()))
                    .toList();

            Map<String, Object> requestBody = Map.of(
                    "model", config.getModel(),
                    "query", query,
                    "top_n", topK,
                    "documents", documents
            );

            log.info("Calling Jina Rerank API for {} candidates (model: {})", candidates.size(), config.getModel());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(config.getBaseUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> resultsList = (List<Map<String, Object>>) response.get("results");

                List<SearchResult> reranked = new ArrayList<>();
                for (Map<String, Object> item : resultsList) {
                    int originalIndex = ((Number) item.get("index")).intValue();
                    double relevanceScore = ((Number) item.get("relevance_score")).doubleValue();

                    if (originalIndex >= 0 && originalIndex < candidates.size()) {
                        SearchResult original = candidates.get(originalIndex);
                        reranked.add(new SearchResult(
                                original.chunkId(),
                                original.path(),
                                original.startLine(),
                                original.endLine(),
                                original.symbolName(),
                                (float) relevanceScore,
                                original.snippet()
                        ));
                    }
                }

                log.info("Successfully reranked {} candidates down to {}", candidates.size(), reranked.size());
                return reranked;
            }

        } catch (Exception e) {
            log.warn("Jina Rerank API call failed (falling back to initial hybrid ranking): {}", e.getMessage());
        }

        // Fallback: return topK from initial hybrid candidates
        return candidates.stream().limit(topK).toList();
    }
}
