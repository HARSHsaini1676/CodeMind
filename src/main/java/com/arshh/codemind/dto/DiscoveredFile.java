package com.arshh.codemind.dto;

import java.nio.file.Path;

/**
 * Represents a source code file found in the cloned repository.
 */
public record DiscoveredFile(
        String relativePath, // relative path inside repo, e.g. "src/main/java/Main.java"
        Path path,           // full local file path on disk
        String language      // detected language, e.g. "Java", "Python", "Markdown", "Text"
) {
    public boolean isJava() {
        return "Java".equalsIgnoreCase(language);
    }

    public boolean isMarkdown() {
        return "Markdown".equalsIgnoreCase(language) || relativePath.toLowerCase().endsWith(".md");
    }
}
