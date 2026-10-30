package com.arshh.codemind.common;

/**
 * Thrown when a submitted repository URL violates security or formatting policies.
 * <p>
 * Examples of violations:
 * <ul>
 *   <li>Non-HTTPS schemes (e.g. {@code http://}, {@code ssh://}, {@code file://})</li>
 *   <li>Non-GitHub hosts (only {@code github.com} is currently supported)</li>
 *   <li>Embedded credentials (e.g. {@code https://user:pass@github.com/...})</li>
 *   <li>Malformed URL paths or invalid owner/repository identifiers</li>
 * </ul>
 */
public class InvalidRepositoryUrlException extends RuntimeException {
    public InvalidRepositoryUrlException(String message) {
        super(message);
    }

    public InvalidRepositoryUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}
