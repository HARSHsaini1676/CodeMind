package com.arshh.codemind.dto;

/**
 * A single ranked code match returned from vector search.
 */
public record SearchResult(
        String chunkId,
        String path,
        int startLine,
        int endLine,
        String symbolName,
        float score,
        String snippet
) {}
