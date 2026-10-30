package com.arshh.codemind.retrieval;

import com.arshh.codemind.dto.SearchResult;
import com.arshh.codemind.indexing.EmbeddingService;
import com.arshh.codemind.indexing.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid retrieval service combining exact symbol lookup, file path matching, and semantic vector search.
 * Merges results using Reciprocal Rank Fusion (RRF) for balanced, highly accurate code ranking.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    // Constant k in RRF formula (standard industry default: 60)
    private static final double RRF_K = 60.0;

    // Detects PascalCase or camelCase symbols (e.g. JwtFilter, cloneRepository)
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\b([A-Z][a-zA-Z0-9]+|[a-z]+[A-Z][a-zA-Z0-9]*)\\b");

    // Detects file names and extensions (e.g. pom.xml, application.properties, Dockerfile, README.md)
    private static final Pattern FILE_PATTERN = Pattern.compile("\\b([a-zA-Z0-9_\\-]+\\.(java|properties|xml|yml|yaml|json|md|sql|sh|html|css|py|js|ts)|Dockerfile)\\b", Pattern.CASE_INSENSITIVE);

    private final EmbeddingService embeddingService;
    private final VectorIndexService vectorIndexService;

    public RetrievalService(EmbeddingService embeddingService, VectorIndexService vectorIndexService) {
        this.embeddingService = embeddingService;
        this.vectorIndexService = vectorIndexService;
    }

    /**
     * Executes hybrid retrieval combining exact keyword matches (symbols + paths) with semantic vector search via RRF.
     */
    public List<SearchResult> retrieve(String repositoryId, String query, int topK) {
        // Map to collect and merge candidate chunks by chunkId
        Map<String, SearchResult> candidates = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        // List 1: Exact Keyword matches (Symbols + File Paths)
        List<SearchResult> keywordMatches = new ArrayList<>();

        // 1a. Detect potential symbol names (e.g. JwtFilter)
        List<String> symbols = extractPotentialSymbols(query);
        for (String symbol : symbols) {
            List<SearchResult> matches = vectorIndexService.searchBySymbol(repositoryId, symbol, 5);
            keywordMatches.addAll(matches);
        }

        // 1b. Detect potential file names/paths (e.g. pom.xml, application.properties)
        List<String> fileNames = extractPotentialFiles(query);
        for (String fileName : fileNames) {
            List<SearchResult> matches = vectorIndexService.searchByPath(repositoryId, fileName, 5);
            keywordMatches.addAll(matches);
        }

        // Apply RRF score for keyword ranked list
        for (int rank = 0; rank < keywordMatches.size(); rank++) {
            SearchResult result = keywordMatches.get(rank);
            candidates.putIfAbsent(result.chunkId(), result);
            double rrfScore = 1.0 / (RRF_K + (rank + 1));
            rrfScores.merge(result.chunkId(), rrfScore, Double::sum);
        }

        // List 2: Semantic vector search
        float[] queryVector = embeddingService.embedQuery(query);
        List<SearchResult> vectorMatches = vectorIndexService.search(repositoryId, queryVector, topK * 2);

        // Apply RRF score for semantic vector ranked list
        for (int rank = 0; rank < vectorMatches.size(); rank++) {
            SearchResult result = vectorMatches.get(rank);
            candidates.putIfAbsent(result.chunkId(), result);
            double rrfScore = 1.0 / (RRF_K + (rank + 1));
            rrfScores.merge(result.chunkId(), rrfScore, Double::sum);
        }

        // Sort all candidates by descending RRF score
        List<SearchResult> rankedResults = new ArrayList<>();
        candidates.values().stream()
                .sorted((a, b) -> Double.compare(
                        rrfScores.getOrDefault(b.chunkId(), 0.0),
                        rrfScores.getOrDefault(a.chunkId(), 0.0)
                ))
                .limit(topK)
                .forEach(chunk -> {
                    // Attach normalized RRF score
                    float fusedScore = rrfScores.getOrDefault(chunk.chunkId(), 0.0).floatValue();
                    rankedResults.add(new SearchResult(
                            chunk.chunkId(),
                            chunk.path(),
                            chunk.startLine(),
                            chunk.endLine(),
                            chunk.symbolName(),
                            fusedScore,
                            chunk.snippet()
                    ));
                });

        return rankedResults;
    }

    private List<String> extractPotentialSymbols(String query) {
        List<String> candidates = new ArrayList<>();
        if (query == null) return candidates;
        Matcher matcher = SYMBOL_PATTERN.matcher(query);
        while (matcher.find()) {
            candidates.add(matcher.group(1));
        }
        return candidates;
    }

    private List<String> extractPotentialFiles(String query) {
        List<String> candidates = new ArrayList<>();
        if (query == null) return candidates;
        Matcher matcher = FILE_PATTERN.matcher(query);
        while (matcher.find()) {
            candidates.add(matcher.group(1));
        }
        return candidates;
    }
}
