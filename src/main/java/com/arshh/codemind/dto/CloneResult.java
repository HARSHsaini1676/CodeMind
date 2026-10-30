package com.arshh.codemind.dto;

import java.nio.file.Path;

/**
 * Encapsulates the immutable result of a successful Git clone operation.
 * <p>
 * This record carries the repository workspace location, the pinned commit SHA,
 * and canonical coordinates required for downstream file discovery, chunking,
 * and citation anchoring.
 *
 * @param repositoryId  Unique internal identifier (UUID) assigned to the repository indexing job.
 * @param workspacePath Absolute file system path to the temporary isolated clone directory.
 * @param commitSha     The exact 40-character Git commit hash (HEAD) indexed, ensuring immutable citations.
 * @param coordinates   Normalized repository metadata (owner, repo name, canonical HTTPS URL).
 * @param durationMs    Total elapsed time in milliseconds taken to perform the shallow clone.
 */
public record CloneResult(
        String repositoryId,
        Path workspacePath,
        String commitSha,
        RepositoryCoordinates coordinates,
        long durationMs
) {
}
