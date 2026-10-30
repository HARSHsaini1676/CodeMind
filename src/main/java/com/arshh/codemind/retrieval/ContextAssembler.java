package com.arshh.codemind.retrieval;

import com.arshh.codemind.dto.SearchResult;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Formats retrieved code chunks into a structured prompt context block for the LLM.
 * Deduplicates repeated chunks, prepends parent file/class headers, and enforces a character budget.
 */
@Component
public class ContextAssembler {

    // Maximum characters allowed in assembled context (~3000 tokens)
    private static final int MAX_CONTEXT_CHARS = 12000;

    /**
     * Combines retrieved chunks into a single readable context string with chunk IDs and parent context.
     */
    public String assembleContext(List<SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "No relevant code found in repository.";
        }

        StringBuilder sb = new StringBuilder();
        Set<String> seenChunkIds = new HashSet<>();

        for (SearchResult result : searchResults) {
            // Skip duplicates if the same chunk was returned multiple times
            if (!seenChunkIds.add(result.chunkId())) {
                continue;
            }

            // Extract simple parent class/file context from relative path
            String parentContext = deriveParentContext(result.path(), result.symbolName());

            String chunkBlock = String.format(
                    """
                    ------------------------------------------------------------
                    [CHUNK_ID: %s]
                    FILE: %s (Lines %d-%d)
                    SCOPE / CONTEXT: %s
                    CODE:
                    %s
                    ------------------------------------------------------------
                    """,
                    result.chunkId(),
                    result.path(),
                    result.startLine(),
                    result.endLine(),
                    parentContext,
                    result.snippet()
            );

            // Stop appending if adding this chunk exceeds our character budget
            if (sb.length() + chunkBlock.length() > MAX_CONTEXT_CHARS) {
                break;
            }

            sb.append(chunkBlock).append("\n");
        }

        return sb.toString();
    }

    /**
     * Gives the LLM parent container awareness (e.g. "Class: UserController" or "Configuration File: application.properties").
     */
    private String deriveParentContext(String path, String symbolName) {
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (symbolName != null && !symbolName.isBlank() && !symbolName.equalsIgnoreCase("N/A")) {
            return "Symbol: " + symbolName + " (Enclosed in " + fileName + ")";
        }
        return "File: " + fileName;
    }
}
