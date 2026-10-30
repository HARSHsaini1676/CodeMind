package com.arshh.codemind.parsing;

import com.arshh.codemind.dto.ChunkType;
import com.arshh.codemind.dto.CodeChunk;
import com.arshh.codemind.dto.DiscoveredFile;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Java source files using JavaParser to extract structured units (methods, constructors, classes).
 * If a Java file has syntax errors, it safely falls back to line-based chunking.
 */
@Component
public class JavaCodeParser {

    private static final Logger log = LoggerFactory.getLogger(JavaCodeParser.class);

    private final GenericTextChunker fallbackChunker;

    public JavaCodeParser(GenericTextChunker fallbackChunker) {
        this.fallbackChunker = fallbackChunker;
    }

    /**
     * Parses a Java file into structured chunks.
     */
    public List<CodeChunk> parse(DiscoveredFile file, String repositoryId, String commitSha) {
        List<CodeChunk> chunks = new ArrayList<>();

        try {
            CompilationUnit cu = StaticJavaParser.parse(file.path());

            // 1. Extract all methods
            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                int start = method.getRange().map(r -> r.begin.line).orElse(1);
                int end = method.getRange().map(r -> r.end.line).orElse(start);
                String chunkId = CodeChunk.generateId(repositoryId, file.relativePath(), start, end);

                chunks.add(new CodeChunk(
                        chunkId, repositoryId, commitSha, file.relativePath(),
                        "Java", ChunkType.METHOD, method.getNameAsString(),
                        start, end, method.toString()
                ));
            }

            // 2. Extract constructors
            for (ConstructorDeclaration constructor : cu.findAll(ConstructorDeclaration.class)) {
                int start = constructor.getRange().map(r -> r.begin.line).orElse(1);
                int end = constructor.getRange().map(r -> r.end.line).orElse(start);
                String chunkId = CodeChunk.generateId(repositoryId, file.relativePath(), start, end);

                chunks.add(new CodeChunk(
                        chunkId, repositoryId, commitSha, file.relativePath(),
                        "Java", ChunkType.METHOD, constructor.getNameAsString(),
                        start, end, constructor.toString()
                ));
            }

            // 3. Extract classes, interfaces, and records (if class has no methods or for high-level structure)
            for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                // If it's a small class, interface, or has no methods, capture the whole type
                if (type.getMethods().isEmpty() || chunks.isEmpty()) {
                    int start = type.getRange().map(r -> r.begin.line).orElse(1);
                    int end = type.getRange().map(r -> r.end.line).orElse(start);
                    String chunkId = CodeChunk.generateId(repositoryId, file.relativePath(), start, end);

                    chunks.add(new CodeChunk(
                            chunkId, repositoryId, commitSha, file.relativePath(),
                            "Java", ChunkType.CLASS, type.getNameAsString(),
                            start, end, type.toString()
                    ));
                }
            }

            for (RecordDeclaration record : cu.findAll(RecordDeclaration.class)) {
                if (chunks.isEmpty() || record.getMethods().isEmpty()) {
                    int start = record.getRange().map(r -> r.begin.line).orElse(1);
                    int end = record.getRange().map(r -> r.end.line).orElse(start);
                    String chunkId = CodeChunk.generateId(repositoryId, file.relativePath(), start, end);

                    chunks.add(new CodeChunk(
                            chunkId, repositoryId, commitSha, file.relativePath(),
                            "Java", ChunkType.CLASS, record.getNameAsString(),
                            start, end, record.toString()
                    ));
                }
            }

            // If AST didn't find any methods or classes, fallback to text chunker
            if (chunks.isEmpty()) {
                return fallbackChunker.chunk(file, repositoryId, commitSha);
            }

            return chunks;

        } catch (Exception e) {
            log.warn("JavaParser failed on [{}], using fallback chunker: {}", file.relativePath(), e.getMessage());
            try {
                return fallbackChunker.chunk(file, repositoryId, commitSha);
            } catch (IOException ioException) {
                log.error("Fallback chunker also failed on [{}]: {}", file.relativePath(), ioException.getMessage());
                return List.of();
            }
        }
    }
}
