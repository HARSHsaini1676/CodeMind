package com.arshh.codemind.dto;

/**
 * Indicates what kind of code structure a chunk represents.
 */
public enum ChunkType {
    CLASS,       // A whole class, interface, or record
    METHOD,      // A method or constructor
    TEXT_BLOCK,  // Cohesive documentation or Markdown section
    FALLBACK     // Plain text / line-based chunk for non-Java files
}
