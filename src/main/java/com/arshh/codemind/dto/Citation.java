package com.arshh.codemind.dto;

/**
 * Verified repository-relative source citation pointing to real code lines.
 */
public record Citation(
        String path,
        int startLine,
        int endLine,
        String chunkId,
        String reason
) {}
