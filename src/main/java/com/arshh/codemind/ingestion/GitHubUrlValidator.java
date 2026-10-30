package com.arshh.codemind.ingestion;

import com.arshh.codemind.common.InvalidRepositoryUrlException;
import com.arshh.codemind.dto.RepositoryCoordinates;
import org.springframework.stereotype.Component;

/**
 * Validates that user input is a valid public GitHub URL.
 * Expected format: https://github.com/owner/repository
 */
@Component
public class GitHubUrlValidator {

    /**
     * Checks if the URL is valid and extracts owner and repository name.
     */
    public RepositoryCoordinates validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new InvalidRepositoryUrlException("Repository URL cannot be empty.");
        }

        String url = rawUrl.trim();

        // Must be an HTTPS GitHub URL
        if (!url.startsWith("https://github.com/")) {
            throw new InvalidRepositoryUrlException("Only public HTTPS GitHub URLs are supported (e.g. https://github.com/owner/repo)");
        }

        // Clean up trailing slash and .git if present
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }

        // Extract owner and repo from path
        String path = url.replace("https://github.com/", "");
        String[] parts = path.split("/");

        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new InvalidRepositoryUrlException("Invalid URL format. Expected: https://github.com/owner/repo");
        }

        String owner = parts[0];
        String repo = parts[1];

        return new RepositoryCoordinates(owner, repo, "https://github.com/" + owner + "/" + repo);
    }
}
