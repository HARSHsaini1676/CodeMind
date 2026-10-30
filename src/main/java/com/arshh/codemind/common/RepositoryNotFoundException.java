package com.arshh.codemind.common;

/**
 * Thrown when a requested repositoryId is not found in the state store.
 */
public class RepositoryNotFoundException extends RuntimeException {
    public RepositoryNotFoundException(String message) {
        super(message);
    }
}
