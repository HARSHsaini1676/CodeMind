package com.arshh.codemind.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload sent by the client to register a GitHub repository for indexing.
 */
public record RegisterRepositoryRequest(
        @NotBlank(message = "Repository URL must not be blank")
        String url
) {}
