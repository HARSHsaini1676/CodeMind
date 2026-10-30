package com.arshh.codemind.dto;

/**
 * Stores the in-memory state of a repository indexing job.
 */
public record RepositoryRecord(
        String repositoryId,
        String githubUrl,
        RepositoryStatus status,
        String commitSha,
        int fileCount,
        int chunkCount,
        String errorMessage
) {
    // Helper to return an updated record with a new status and optional error
    public RepositoryRecord withStatus(RepositoryStatus newStatus, String error) {
        return new RepositoryRecord(repositoryId, githubUrl, newStatus, commitSha, fileCount, chunkCount, error);
    }

    // Helper to return an updated record when indexing completes successfully
    public RepositoryRecord withSuccess(String sha, int files, int chunks) {
        return new RepositoryRecord(repositoryId, githubUrl, RepositoryStatus.READY, sha, files, chunks, null);
    }
}
