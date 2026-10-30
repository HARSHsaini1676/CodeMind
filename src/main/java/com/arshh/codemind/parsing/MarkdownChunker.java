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
 * Splits Markdown files by section headers (#, ##, ###) so related documentation stays cohesive.
 */
@Component
public class MarkdownChunker {

    public List<CodeChunk> chunk(DiscoveredFile file, String repositoryId, String commitSha) throws IOException {
        List<String> lines = Files.readAllLines(file.path());
        if (lines.isEmpty()) {
            return List.of();
        }

        List<CodeChunk> chunks = new ArrayList<>();
        List<String> currentChunkLines = new ArrayList<>();
        int chunkStartLine = 1;
        String currentHeading = "Overview";

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNumber = i + 1;

            // Detect Markdown headers: # Header, ## Subheader, etc.
            if (line.trim().startsWith("#") && !currentChunkLines.isEmpty()) {
                // Save accumulated section as a chunk
                String content = String.join("\n", currentChunkLines).trim();
                if (!content.isEmpty()) {
                    int chunkEndLine = lineNumber - 1;
                    String chunkId = CodeChunk.generateId(repositoryId, commitSha, file.relativePath(), chunkStartLine, chunkEndLine);
                    chunks.add(new CodeChunk(
                            chunkId,
                            repositoryId,
                            commitSha,
                            file.relativePath(),
                            file.language(),
                            ChunkType.TEXT_BLOCK,
                            currentHeading,
                            chunkStartLine,
                            chunkEndLine,
                            content
                    ));
                }

                currentChunkLines.clear();
                chunkStartLine = lineNumber;
                currentHeading = line.replaceAll("^#+\\s*", "").trim();
            }

            currentChunkLines.add(line);
        }

        // Flush remaining lines
        if (!currentChunkLines.isEmpty()) {
            String content = String.join("\n", currentChunkLines).trim();
            if (!content.isEmpty()) {
                int chunkEndLine = lines.size();
                String chunkId = CodeChunk.generateId(repositoryId, commitSha, file.relativePath(), chunkStartLine, chunkEndLine);
                chunks.add(new CodeChunk(
                        chunkId,
                        repositoryId,
                        commitSha,
                        file.relativePath(),
                        file.language(),
                        ChunkType.TEXT_BLOCK,
                        currentHeading,
                        chunkStartLine,
                        chunkEndLine,
                        content
                ));
            }
        }

        return chunks;
    }
}
