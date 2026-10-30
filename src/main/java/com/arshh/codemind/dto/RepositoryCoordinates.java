package com.arshh.codemind.dto;

/**
 * Immutable coordinates representing a verified public GitHub repository.
 *
 * @param owner        The GitHub organization or username owning the repository (e.g. "spring-projects").
 * @param repoName     The name of the repository without .git suffix (e.g. "spring-petclinic").
 * @param canonicalUrl The normalized HTTPS URL (e.g. "https://github.com/spring-projects/spring-petclinic").
 */
public record RepositoryCoordinates(
        String owner,
        String repoName,
        String canonicalUrl
) {
}
