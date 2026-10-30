package com.arshh.codemind.ingestion;

import com.arshh.codemind.config.CodeMindProperties;
import com.arshh.codemind.dto.DiscoveredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans the cloned repository folder to discover code files.
 * Skips build folders (.git, target, node_modules) and media/binary files.
 */
@Service
public class FileDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(FileDiscoveryService.class);

    // Folders we want to ignore
    private static final Set<String> IGNORED_FOLDERS = Set.of(
            ".git", "target", "build", "node_modules", ".idea", ".vscode", ".mvn", "bin"
    );

    // File extensions we want to skip (images, compiled classes, archives)
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "jar", "war", "class", "exe", "dll", "zip", "tar", "gz",
            "png", "jpg", "jpeg", "gif", "ico", "pdf", "mp4", "mp3"
    );

    private final CodeMindProperties properties;

    public FileDiscoveryService(CodeMindProperties properties) {
        this.properties = properties;
    }

    /**
     * Walks through the repo folder and returns a list of candidate source files.
     */
    public List<DiscoveredFile> discoverFiles(Path workspacePath) throws IOException {
        List<DiscoveredFile> result = new ArrayList<>();
        long maxSizeBytes = (long) properties.getMaxFileSizeKb() * 1024L;

        try (Stream<Path> stream = Files.walk(workspacePath)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                // Skip if file is inside an ignored folder (.git, target, etc.)
                if (isInIgnoredFolder(workspacePath, file)) {
                    return;
                }

                // Skip empty files or files that are too large
                try {
                    long size = Files.size(file);
                    if (size == 0 || size > maxSizeBytes) {
                        return;
                    }
                } catch (IOException e) {
                    return;
                }

                String fileName = file.getFileName().toString();
                String ext = getFileExtension(fileName);

                // Skip binary files and sensitive .env files
                if (BINARY_EXTENSIONS.contains(ext) || fileName.startsWith(".env")) {
                    return;
                }

                // Use forward slashes so paths look standard across Windows and Linux
                String relativePath = workspacePath.relativize(file).toString().replace('\\', '/');
                String language = detectLanguage(ext);

                result.add(new DiscoveredFile(relativePath, file, language));
            });
        }

        log.info("Discovered {} code files in workspace [{}]", result.size(), workspacePath);
        return result;
    }

    private boolean isInIgnoredFolder(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (IGNORED_FOLDERS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private String detectLanguage(String ext) {
        return switch (ext.toLowerCase()) {
            case "java" -> "Java";
            case "py" -> "Python";
            case "js" -> "JavaScript";
            case "ts" -> "TypeScript";
            case "html" -> "HTML";
            case "css" -> "CSS";
            case "json" -> "JSON";
            case "xml" -> "XML";
            case "yml", "yaml" -> "YAML";
            case "sql" -> "SQL";
            case "md" -> "Markdown";
            default -> "Text";
        };
    }

    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 && dot < fileName.length() - 1) ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
