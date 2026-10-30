package com.arshh.codemind.dto;

/**
 * Response payload reporting current indexing status of a repository.
 */
public record RepositoryStatusResponse(
        String repositoryId,
        RepositoryStatus status,
        String commitSha,
        int fileCount,
        int chunkCount,
        String errorMessage
) {}
