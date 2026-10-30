package com.arshh.codemind.dto;

import java.util.List;

/**
 * Final grounded answer response with server-verified citations and limitations.
 */
public record AskResponse(
        String answer,
        List<Citation> citations,
        List<String> limitations
) {}
