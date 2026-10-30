package com.arshh.codemind.common;

/**
 * Thrown when attempting to search or ask against a repository that has not finished indexing.
 */
public class RepositoryNotReadyException extends RuntimeException {
    public RepositoryNotReadyException(String message) {
        super(message);
    }
}
