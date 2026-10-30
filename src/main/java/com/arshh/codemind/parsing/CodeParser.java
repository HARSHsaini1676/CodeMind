package com.arshh.codemind.parsing;

import com.arshh.codemind.dto.CodeChunk;
import com.arshh.codemind.dto.DiscoveredFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Main coordinator for chunking files.
 * Routes Java files to the AST parser, Markdown files to section chunker, and other files to line chunker.
 */
@Service
public class CodeParser {

    private final JavaCodeParser javaParser;
    private final MarkdownChunker markdownChunker;
    private final GenericTextChunker textChunker;

    public CodeParser(JavaCodeParser javaParser, MarkdownChunker markdownChunker, GenericTextChunker textChunker) {
        this.javaParser = javaParser;
        this.markdownChunker = markdownChunker;
        this.textChunker = textChunker;
    }

    /**
     * Splits a discovered file into searchable code chunks.
     */
    public List<CodeChunk> parseFile(DiscoveredFile file, String repositoryId, String commitSha) {
        if (file.isJava()) {
            return javaParser.parse(file, repositoryId, commitSha);
        } else if (file.isMarkdown()) {
            try {
                return markdownChunker.chunk(file, repositoryId, commitSha);
            } catch (IOException e) {
                return List.of();
            }
        } else {
            try {
                return textChunker.chunk(file, repositoryId, commitSha);
            } catch (IOException e) {
                return List.of();
            }
        }
    }
}
