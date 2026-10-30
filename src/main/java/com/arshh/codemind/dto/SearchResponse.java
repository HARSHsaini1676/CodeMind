package com.arshh.codemind.dto;

import java.util.List;

/**
 * Response payload containing ranked code matches for a search query.
 */
public record SearchResponse(
        List<SearchResult> results
) {}
