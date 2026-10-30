package com.arshh.codemind.common;

/**
 * Thrown when an error occurs during the Git clone or Git inspection lifecycle.
 * <p>
 * Scenarios where this exception is raised:
 * <ul>
 *   <li>The Git process exceeds the configured timeout threshold.</li>
 *   <li>The Git command exits with a non-zero exit status (e.g. repository not found, network failure).</li>
 *   <li>Directory creation or file-system workspace operations fail.</li>
 *   <li>The commit SHA cannot be parsed from the repository HEAD.</li>
 * </ul>
 */
public class GitCloneException extends RuntimeException {
    public GitCloneException(String message) {
        super(message);
    }

    public GitCloneException(String message, Throwable cause) {
        super(message, cause);
    }
}
