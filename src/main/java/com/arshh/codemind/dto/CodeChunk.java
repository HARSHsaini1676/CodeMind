package com.arshh.codemind.dto;

import java.util.UUID;

/**
 * Represents a single searchable unit of code (a method, class, or text block).
 */
public record CodeChunk(
        String chunkId,         // unique stable ID (e.g. UUID)
        String repositoryId,    // which repository this belongs to
        String commitSha,       // exact git commit SHA for citation accuracy
        String path,            // relative file path (e.g. "src/main/java/App.java")
        String language,        // language (e.g. "Java", "Python")
        ChunkType chunkType,    // CLASS, METHOD, or FALLBACK
        String symbolName,      // name of method or class (can be null/empty for text)
        int startLine,          // starting line in source file (1-based)
        int endLine,            // ending line in source file (1-based)
        String content          // the actual code snippet
) {
    /**
     * Generates a stable UUID based on repo, commit SHA, file path, and line numbers.
     * Re-indexing the same code produces the exact same ID.
     */
    public static String generateId(String repositoryId, String commitSha, String path, int startLine, int endLine) {
        String raw = repositoryId + ":" + (commitSha != null ? commitSha : "head") + ":" + path + ":" + startLine + "-" + endLine;
        return UUID.nameUUIDFromBytes(raw.getBytes()).toString();
    }

    public static String generateId(String repositoryId, String path, int startLine, int endLine) {
        return generateId(repositoryId, "head", path, startLine, endLine);
    }
}
