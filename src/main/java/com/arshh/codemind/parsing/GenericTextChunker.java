package com.arshh.codemind.parsing;

import com.arshh.codemind.dto.ChunkType;
import com.arshh.codemind.dto.CodeChunk;
import com.arshh.codemind.dto.DiscoveredFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Line-based chunker for non-Java files (Python, JavaScript, Markdown, etc.).
 * Splits files into blocks of lines with slight overlap so context isn't lost at edges.
 */
@Component
public class GenericTextChunker {

    private static final int CHUNK_LINES = 50;  // lines per chunk
    private static final int OVERLAP_LINES = 10; // lines repeated in next chunk

    public List<CodeChunk> chunk(DiscoveredFile file, String repositoryId, String commitSha) throws IOException {
        List<CodeChunk> chunks = new ArrayList<>();
        List<String> lines = Files.readAllLines(file.path());

        if (lines.isEmpty()) {
            return chunks;
        }

        // If file is smaller than chunk size, take the whole file as 1 chunk
        if (lines.size() <= CHUNK_LINES) {
            String content = String.join("\n", lines);
            String chunkId = CodeChunk.generateId(repositoryId, file.relativePath(), 1, lines.size());
            chunks.add(new CodeChunk(
                    chunkId, repositoryId, commitSha, file.relativePath(),
                    file.language(), ChunkType.FALLBACK, null,
                    1, lines.size(), content
            ));
            return chunks;
        }

        // Sliding window over lines
        int start = 0;
        while (start < lines.size()) {
            int end = Math.min(start + CHUNK_LINES, lines.size());
            List<String> subList = lines.subList(start, end);
            String content = String.join("\n", subList);

            int startLine = start + 1; // 1-based line numbers
            int endLine = end;

            String chunkId = CodeChunk.generateId(repositoryId, file.relativePath(), startLine, endLine);
            chunks.add(new CodeChunk(
                    chunkId, repositoryId, commitSha, file.relativePath(),
                    file.language(), ChunkType.FALLBACK, null,
                    startLine, endLine, content
            ));

            if (end >= lines.size()) {
                break;
            }
            start += (CHUNK_LINES - OVERLAP_LINES);
        }

        return chunks;
    }
}
