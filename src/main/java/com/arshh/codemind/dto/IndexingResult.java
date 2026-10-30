package com.arshh.codemind.dto;

/**
 * Summary metrics of a completed repository indexing job.
 */
public record IndexingResult(
        String repositoryId,
        String commitSha,
        int fileCount,
        int chunkCount,
        long totalDurationMs
) {
}
