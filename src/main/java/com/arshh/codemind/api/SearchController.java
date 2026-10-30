package com.arshh.codemind.api;

import com.arshh.codemind.common.RepositoryNotFoundException;
import com.arshh.codemind.common.RepositoryNotReadyException;
import com.arshh.codemind.dto.RepositoryRecord;
import com.arshh.codemind.dto.RepositoryStatus;
import com.arshh.codemind.dto.SearchRequest;
import com.arshh.codemind.dto.SearchResponse;
import com.arshh.codemind.dto.SearchResult;
import com.arshh.codemind.repository.RepositoryStateStore;
import com.arshh.codemind.retrieval.RetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for executing semantic and hybrid code search within indexed repositories.
 */
@RestController
@RequestMapping("/api/v1/repositories")
@Tag(name = "Search", description = "Endpoints for code search")
public class SearchController {

    private final RepositoryStateStore repositoryStateStore;
    private final RetrievalService retrievalService;

    public SearchController(
            RepositoryStateStore repositoryStateStore,
            RetrievalService retrievalService) {
        this.repositoryStateStore = repositoryStateStore;
        this.retrievalService = retrievalService;
    }

    /**
     * Performs hybrid search combining exact symbol match and vector similarity on indexed code.
     */
    @PostMapping("/{id}/search")
    @Operation(summary = "Code search", description = "Searches code snippets in the repository using hybrid retrieval")
    public ResponseEntity<SearchResponse> searchCode(
            @PathVariable("id") String id,
            @Valid @RequestBody SearchRequest request) {

        // Ensure the repository exists
        RepositoryRecord record = repositoryStateStore.findById(id)
                .orElseThrow(() -> new RepositoryNotFoundException("Repository not found: " + id));

        // Refuse search if repository is still indexing or has failed
        if (record.status() != RepositoryStatus.READY) {
            throw new RepositoryNotReadyException("Repository is not ready for search. Current status: " + record.status());
        }

        // Perform hybrid retrieval (exact symbol + semantic vector search)
        List<SearchResult> results = retrievalService.retrieve(id, request.query(), request.effectiveTopK());

        return ResponseEntity.ok(new SearchResponse(results));
    }
}
