package com.arshh.codemind.retrieval;

import com.arshh.codemind.dto.SearchResult;

import java.util.List;

/**
 * Interface for scoring and reordering retrieved candidate chunks against the user query.
 */
public interface RerankingService {

    /**
     * Reranks candidate search results and returns the topK most relevant items.
     *
     * @param query      The natural-language question or search query.
     * @param candidates The list of candidate snippets retrieved by hybrid/vector search.
     * @param topK       The desired number of final reranked results.
     * @return Re-scored and sorted list truncated to topK.
     */
    List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK);
}
