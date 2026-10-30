package com.arshh.codemind.ingestion;

import com.arshh.codemind.common.GitCloneException;
import com.arshh.codemind.config.CodeMindProperties;
import com.arshh.codemind.dto.CloneResult;
import com.arshh.codemind.dto.RepositoryCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Clones a public GitHub repository locally using the standard Git command line.
 */
@Service
public class GitCloneService {

    private static final Logger log = LoggerFactory.getLogger(GitCloneService.class);

    private final GitHubUrlValidator urlValidator;
    private final CodeMindProperties properties;

    public GitCloneService(GitHubUrlValidator urlValidator, CodeMindProperties properties) {
        this.urlValidator = urlValidator;
        this.properties = properties;
    }

    /**
     * Clones the repository into the workspaces/{repoId} folder.
     */
    public CloneResult cloneRepository(String repositoryId, String rawUrl) {
        RepositoryCoordinates coords = urlValidator.validateAndNormalize(rawUrl);

        Path workspacePath = Paths.get(properties.getWorkspaceDir(), repositoryId).toAbsolutePath().normalize();

        // Wipe any previous folder with this ID
        cleanupWorkspace(workspacePath);

        try {
            Files.createDirectories(workspacePath.getParent());
        } catch (Exception e) {
            throw new GitCloneException("Could not create workspace directory: " + e.getMessage(), e);
        }

        log.info("Cloning {} into {}", coords.canonicalUrl(), workspacePath);
        long startTime = System.currentTimeMillis();

        // Run: git clone --depth 1 <url> <workspacePath>
        runCommand(
                null,
                properties.getCloneTimeoutSeconds(),
                "git", "clone", "--depth", "1", coords.canonicalUrl(), workspacePath.toString()
        );

        long durationMs = System.currentTimeMillis() - startTime;

        // Get the commit hash using: git rev-parse HEAD
        String commitSha = runCommand(workspacePath.toFile(), 10, "git", "rev-parse", "HEAD");

        log.info("Cloned {} successfully in {} ms (commit: {})", coords.repoName(), durationMs, commitSha);
        return new CloneResult(repositoryId, workspacePath, commitSha, coords, durationMs);
    }

    /**
     * Deletes the workspace directory and all its files.
     */
    public void cleanupWorkspace(Path workspacePath) {
        if (workspacePath == null || !Files.exists(workspacePath)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(workspacePath)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            log.warn("Failed to delete workspace {}: {}", workspacePath, e.getMessage());
        }
    }

    /**
     * Helper to run an external command (like git) and return its output.
     */
    private String runCommand(File workingDir, int timeoutSeconds, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                pb.directory(workingDir);
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitCloneException("Command timed out: " + String.join(" ", command));
            }

            if (process.exitValue() != 0) {
                throw new GitCloneException("Command failed: " + output.toString().trim());
            }

            return output.toString().trim();
        } catch (Exception e) {
            throw new GitCloneException("Git error: " + e.getMessage(), e);
        }
    }
}
