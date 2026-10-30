package com.arshh.codemind.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload when asking a natural-language question about an indexed repository.
 */
public record AskRequest(
        @NotBlank(message = "Question must not be blank")
        String question,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must be at most 20")
        Integer topK
) {
    // Helper to supply default topK of 8 when not specified by the client
    public int effectiveTopK() {
        return (topK != null && topK > 0) ? topK : 8;
    }
}
