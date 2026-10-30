package com.arshh.codemind.repository;

import com.arshh.codemind.dto.RepositoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA Entity mapping repository indexing state to the PostgreSQL "repositories" table.
 */
@Entity
@Table(name = "repositories")
public class RepositoryEntity {

    @Id
    @Column(name = "repository_id", nullable = false, length = 64)
    private String repositoryId;

    @Column(name = "github_url", nullable = false, length = 512)
    private String githubUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RepositoryStatus status;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public RepositoryEntity() {}

    public RepositoryEntity(
            String repositoryId,
            String githubUrl,
            RepositoryStatus status,
            String commitSha,
            int fileCount,
            int chunkCount,
            String errorMessage) {
        this.repositoryId = repositoryId;
        this.githubUrl = githubUrl;
        this.status = status;
        this.commitSha = commitSha;
        this.fileCount = fileCount;
        this.chunkCount = chunkCount;
        this.errorMessage = errorMessage;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getRepositoryId() { return repositoryId; }
    public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }

    public RepositoryStatus getStatus() { return status; }
    public void setStatus(RepositoryStatus status) { this.status = status; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
