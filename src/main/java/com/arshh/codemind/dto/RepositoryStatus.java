package com.arshh.codemind.dto;

/**
 * Tracks the indexing lifecycle of a cloned repository.
 */
public enum RepositoryStatus {
    // Request accepted and placed in queue
    QUEUED,
    // Cloning files, generating embeddings, or upserting to Qdrant
    INDEXING,
    // Successfully indexed and ready to answer queries
    READY,
    // Error occurred during ingestion or indexing
    FAILED
}
