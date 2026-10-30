package com.arshh.codemind.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for semantic search within an indexed repository.
 */
public record SearchRequest(
        @NotBlank(message = "Query must not be blank")
        String query,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 50, message = "topK must be at most 50")
        Integer topK
) {
    // Helper to supply default topK of 8 when not specified by the user
    public int effectiveTopK() {
        return (topK != null && topK > 0) ? topK : 8;
    }
}
