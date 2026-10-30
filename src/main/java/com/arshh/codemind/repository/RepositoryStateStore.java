package com.arshh.codemind.repository;

import com.arshh.codemind.dto.RepositoryRecord;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Thread-safe and persistent store for repository indexing states backed by PostgreSQL.
 */
@Repository
public class RepositoryStateStore {

    private final JpaRepositoryRecordStore jpaStore;

    public RepositoryStateStore(JpaRepositoryRecordStore jpaStore) {
        this.jpaStore = jpaStore;
    }

    /**
     * Saves or updates a repository record in PostgreSQL.
     */
    public void save(RepositoryRecord record) {
        RepositoryEntity entity = jpaStore.findById(record.repositoryId())
                .orElse(new RepositoryEntity(
                        record.repositoryId(),
                        record.githubUrl(),
                        record.status(),
                        record.commitSha(),
                        record.fileCount(),
                        record.chunkCount(),
                        record.errorMessage()
                ));

        entity.setGithubUrl(record.githubUrl());
        entity.setStatus(record.status());
        entity.setCommitSha(record.commitSha());
        entity.setFileCount(record.fileCount());
        entity.setChunkCount(record.chunkCount());
        entity.setErrorMessage(record.errorMessage());
        entity.setUpdatedAt(LocalDateTime.now());

        jpaStore.save(entity);
    }

    /**
     * Retrieves a repository record from PostgreSQL by ID.
     */
    public Optional<RepositoryRecord> findById(String repositoryId) {
        return jpaStore.findById(repositoryId).map(entity -> new RepositoryRecord(
                entity.getRepositoryId(),
                entity.getGithubUrl(),
                entity.getStatus(),
                entity.getCommitSha(),
                entity.getFileCount(),
                entity.getChunkCount(),
                entity.getErrorMessage()
        ));
    }
}
