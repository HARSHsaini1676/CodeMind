package com.arshh.codemind.dto;

/**
 * Immediate response after accepting a repository for background indexing.
 */
public record RegisterRepositoryResponse(
        String repositoryId,
        RepositoryStatus status,
        String message
) {}
