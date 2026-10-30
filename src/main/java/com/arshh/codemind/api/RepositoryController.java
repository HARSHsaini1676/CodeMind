package com.arshh.codemind.api;

import com.arshh.codemind.common.RepositoryNotFoundException;
import com.arshh.codemind.dto.RegisterRepositoryRequest;
import com.arshh.codemind.dto.RegisterRepositoryResponse;
import com.arshh.codemind.dto.RepositoryCoordinates;
import com.arshh.codemind.dto.RepositoryRecord;
import com.arshh.codemind.dto.RepositoryStatus;
import com.arshh.codemind.dto.RepositoryStatusResponse;
import com.arshh.codemind.ingestion.GitHubUrlValidator;
import com.arshh.codemind.ingestion.IndexingService;
import com.arshh.codemind.repository.RepositoryStateStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST API for submitting public GitHub repositories and polling their indexing status.
 */
@RestController
@RequestMapping("/api/v1/repositories")
@Tag(name = "Repositories", description = "Endpoints for repository ingestion and status tracking")
public class RepositoryController {

    private final GitHubUrlValidator gitHubUrlValidator;
    private final RepositoryStateStore repositoryStateStore;
    private final IndexingService indexingService;

    public RepositoryController(
            GitHubUrlValidator gitHubUrlValidator,
            RepositoryStateStore repositoryStateStore,
            IndexingService indexingService) {
        this.gitHubUrlValidator = gitHubUrlValidator;
        this.repositoryStateStore = repositoryStateStore;
        this.indexingService = indexingService;
    }

    /**
     * Submits a public GitHub URL to be cloned, chunked, and indexed asynchronously.
     */
    @PostMapping
    @Operation(summary = "Register repository", description = "Submits a public GitHub repository for background indexing")
    public ResponseEntity<RegisterRepositoryResponse> registerRepository(
            @Valid @RequestBody RegisterRepositoryRequest request) {

        // Validate that the URL is a safe, public HTTPS GitHub repository and normalize it
        RepositoryCoordinates coordinates = gitHubUrlValidator.validateAndNormalize(request.url());

        // Assign a stable unique ID to track this repository
        String repositoryId = UUID.randomUUID().toString();

        // Save initial status as QUEUED
        RepositoryRecord initialRecord = new RepositoryRecord(
                repositoryId,
                coordinates.canonicalUrl(),
                RepositoryStatus.QUEUED,
                null,
                0,
                0,
                null
        );
        repositoryStateStore.save(initialRecord);

        // Start background indexing without blocking HTTP response
        indexingService.startIndexingAsync(repositoryId, coordinates.canonicalUrl());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new RegisterRepositoryResponse(repositoryId, RepositoryStatus.QUEUED, "Repository accepted for indexing."));
    }

    /**
     * Retrieves current indexing status and metadata for a repository.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get repository status", description = "Fetches the indexing status, commit SHA, file count, and chunk count")
    public ResponseEntity<RepositoryStatusResponse> getRepositoryStatus(@PathVariable("id") String id) {
        RepositoryRecord record = repositoryStateStore.findById(id)
                .orElseThrow(() -> new RepositoryNotFoundException("Repository not found: " + id));

        return ResponseEntity.ok(new RepositoryStatusResponse(
                record.repositoryId(),
                record.status(),
                record.commitSha(),
                record.fileCount(),
                record.chunkCount(),
                record.errorMessage()
        ));
    }
}
