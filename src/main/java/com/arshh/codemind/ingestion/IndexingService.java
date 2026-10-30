package com.arshh.codemind.ingestion;

import com.arshh.codemind.dto.CloneResult;
import com.arshh.codemind.dto.CodeChunk;
import com.arshh.codemind.dto.DiscoveredFile;
import com.arshh.codemind.dto.IndexingResult;
import com.arshh.codemind.dto.RepositoryStatus;
import com.arshh.codemind.indexing.EmbeddingService;
import com.arshh.codemind.indexing.VectorIndexService;
import com.arshh.codemind.parsing.CodeParser;
import com.arshh.codemind.repository.RepositoryStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates the end-to-end repository indexing pipeline:
 * 1. Clones GitHub repository into a temporary folder
 * 2. Discovers and filters valid source code files
 * 3. Chunks files using Java AST parser or text fallback
 * 4. Generates vector embeddings for all code chunks
 * 5. Stores chunks and vectors in Qdrant
 * 6. Deletes the temporary workspace folder
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final GitCloneService gitCloneService;
    private final FileDiscoveryService fileDiscoveryService;
    private final CodeParser codeParser;
    private final EmbeddingService embeddingService;
    private final VectorIndexService vectorIndexService;
    private final RepositoryStateStore repositoryStateStore;

    // Small thread pool to run indexing jobs in the background without blocking HTTP requests
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public IndexingService(
            GitCloneService gitCloneService,
            FileDiscoveryService fileDiscoveryService,
            CodeParser codeParser,
            EmbeddingService embeddingService,
            VectorIndexService vectorIndexService,
            RepositoryStateStore repositoryStateStore) {
        this.gitCloneService = gitCloneService;
        this.fileDiscoveryService = fileDiscoveryService;
        this.codeParser = codeParser;
        this.embeddingService = embeddingService;
        this.vectorIndexService = vectorIndexService;
        this.repositoryStateStore = repositoryStateStore;
    }

    /**
     * Starts the indexing pipeline in the background and updates the repository state store.
     */
    public void startIndexingAsync(String repositoryId, String githubUrl) {
        executor.submit(() -> {
            try {
                // Update status from QUEUED to INDEXING
                repositoryStateStore.findById(repositoryId).ifPresent(record ->
                        repositoryStateStore.save(record.withStatus(RepositoryStatus.INDEXING, null)));

                // Execute the indexing pipeline
                IndexingResult result = indexRepository(repositoryId, githubUrl);

                // Update status to READY on completion
                repositoryStateStore.findById(repositoryId).ifPresent(record ->
                        repositoryStateStore.save(record.withSuccess(result.commitSha(), result.fileCount(), result.chunkCount())));

            } catch (Exception e) {
                log.error("Indexing failed for repository [{}]: {}", repositoryId, e.getMessage(), e);
                // Mark status as FAILED with error message
                repositoryStateStore.findById(repositoryId).ifPresent(record ->
                        repositoryStateStore.save(record.withStatus(RepositoryStatus.FAILED, e.getMessage())));
            }
        });
    }

    /**
     * Executes the complete indexing pipeline for a repository synchronously.
     */
    public IndexingResult indexRepository(String repositoryId, String githubUrl) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("Starting indexing job [{}] for URL: {}", repositoryId, githubUrl);

        // Step 1: Clone repository
        CloneResult cloneResult = gitCloneService.cloneRepository(repositoryId, githubUrl);

        try {
            // Step 2: Discover code files
            List<DiscoveredFile> files = fileDiscoveryService.discoverFiles(cloneResult.workspacePath());
            log.info("Found {} candidate files to index", files.size());

            // Step 3: Chunk all files
            List<CodeChunk> allChunks = new ArrayList<>();
            for (DiscoveredFile file : files) {
                List<CodeChunk> fileChunks = codeParser.parseFile(file, repositoryId, cloneResult.commitSha());
                allChunks.addAll(fileChunks);
            }
            log.info("Extracted {} code chunks from {} files", allChunks.size(), files.size());

            if (!allChunks.isEmpty()) {
                // Step 4: Create embeddings in batches
                List<float[]> embeddings = embeddingService.embedChunks(allChunks);

                // Step 5: Ensure collection exists in Qdrant
                int dimensions = embeddings.get(0).length;
                vectorIndexService.ensureCollectionExists(dimensions);

                // Step 6: Upsert into Qdrant
                vectorIndexService.upsertChunks(allChunks, embeddings);
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("Indexing job [{}] completed in {} ms (files: {}, chunks: {})",
                    repositoryId, totalDuration, files.size(), allChunks.size());

            return new IndexingResult(repositoryId, cloneResult.commitSha(), files.size(), allChunks.size(), totalDuration);

        } finally {
            // Step 7: Clean up temporary clone workspace folder
            gitCloneService.cleanupWorkspace(cloneResult.workspacePath());
        }
    }
}
