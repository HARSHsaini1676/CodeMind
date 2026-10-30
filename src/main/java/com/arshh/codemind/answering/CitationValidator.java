package com.arshh.codemind.answering;

import com.arshh.codemind.dto.Citation;
import com.arshh.codemind.dto.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates that every citation returned by the LLM maps to a genuine chunk retrieved from Qdrant.
 * Discards hallucinated citations, preventing fake line numbers or imaginary file paths from reaching the client.
 */
@Component
public class CitationValidator {

    // Regex pattern matching chunk ID references in LLM text (e.g. [CHUNK_ID: abc-123])
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile("\\[CHUNK_ID:\\s*([a-zA-Z0-9\\-]+)\\]");

    /**
     * Resolves chunk IDs found in LLM text or citations list into server-verified file paths and line ranges.
     */
    public List<Citation> validateCitations(String llmAnswer, List<SearchResult> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return Collections.emptyList();
        }

        // Index candidate chunks by their stable chunkId for fast O(1) lookup
        Map<String, SearchResult> chunkMap = retrievedChunks.stream()
                .filter(c -> c.chunkId() != null && !c.chunkId().isBlank())
                .collect(Collectors.toMap(SearchResult::chunkId, c -> c, (existing, replacement) -> existing));

        List<Citation> verifiedCitations = new ArrayList<>();
        List<String> seenChunkIds = new ArrayList<>();

        // Scan the LLM response text for referenced chunk IDs
        Matcher matcher = CHUNK_ID_PATTERN.matcher(llmAnswer != null ? llmAnswer : "");
        while (matcher.find()) {
            String chunkId = matcher.group(1);
            if (!seenChunkIds.contains(chunkId) && chunkMap.containsKey(chunkId)) {
                seenChunkIds.add(chunkId);
                SearchResult match = chunkMap.get(chunkId);
                verifiedCitations.add(new Citation(
                        match.path(),
                        match.startLine(),
                        match.endLine(),
                        match.chunkId(),
                        "Referenced in code evidence"
                ));
            }
        }

        // If the model didn't use bracket syntax, fall back to matching top relevant chunks
        if (verifiedCitations.isEmpty() && !retrievedChunks.isEmpty()) {
            SearchResult topHit = retrievedChunks.get(0);
            verifiedCitations.add(new Citation(
                    topHit.path(),
                    topHit.startLine(),
                    topHit.endLine(),
                    topHit.chunkId(),
                    "Top retrieved code candidate"
            ));
        }

        return verifiedCitations;
    }
}
