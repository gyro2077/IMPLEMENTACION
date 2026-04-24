package org.flisol.evidence.web;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActionJobService {

    private static final Logger log = LoggerFactory.getLogger(ActionJobService.class);
    private static final int OUTPUT_LIMIT = 24000;

    private final ActionJobRepository repository;
    private final Path workspaceDir;
    private final String hostName;
    private final String apiBaseUrl;
    private final String targetBaseUrl;
    private final ExecutorService executorService;

    public ActionJobService(ActionJobRepository repository,
                            @Value("${app.actions.workspace-dir:}") String workspaceDir,
                            @Value("${app.actions.host-name:target-cachyos}") String hostName,
                            @Value("${app.actions.api-base-url:http://localhost:8080}") String apiBaseUrl,
                            @Value("${app.actions.target-base-url:http://target-app:8080}") String targetBaseUrl) {
        this.repository = repository;
        this.workspaceDir = resolveWorkspaceDir(workspaceDir);
        this.hostName = hostName;
        this.apiBaseUrl = apiBaseUrl;
        this.targetBaseUrl = targetBaseUrl;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "action-job-runner");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Map<String, Object> runScan() {
        return runAction(ActionType.RUN_SCAN);
    }

    public Map<String, Object> runBackup() {
        return runAction(ActionType.RUN_BACKUP);
    }

    public Map<String, Object> runRestore() {
        return runAction(ActionType.RUN_RESTORE);
    }

    public Map<String, Object> getJobStatus(UUID id) {
        Map<String, Object> job = repository.findById(id);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Action job no encontrado: " + id);
        }
        return job;
    }

    private synchronized Map<String, Object> runAction(ActionType action) {
        if (repository.hasActiveJobs()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Ya existe una accion operativa en ejecucion. Espera a que termine antes de lanzar otra."
            );
        }

        List<List<String>> commandPlan = buildCommandPlan(action);
        String commandLine = commandPlan.stream()
            .map(this::toDisplayCommand)
            .reduce((a, b) -> a + " && " + b)
            .orElse(action.actionName);

        ActionJob job = new ActionJob(UUID.randomUUID(), action.actionName, hostName, "PENDING");
        job.setCommandLine(commandLine);
        repository.save(job);

        executorService.submit(() -> executeJob(job, action, commandPlan));
        return repository.findById(job.getId());
    }

    private void executeJob(ActionJob job, ActionType action, List<List<String>> commandPlan) {
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        repository.update(job);

        StringBuilder output = new StringBuilder();
        try {
            for (List<String> command : commandPlan) {
                appendLimited(output, "$ " + toDisplayCommand(command) + "\n");
                ExecutionResult result = execute(command);
                appendLimited(output, result.output);
                if (result.exitCode != 0) {
                    throw new IllegalStateException("Exit code " + result.exitCode + " en comando: " + toDisplayCommand(command));
                }
            }

            job.setStatus("COMPLETED");
            job.setOutputLog(trimOutput(output.toString()));
            job.setFinishedAt(Instant.now());
            repository.update(job);
        } catch (Exception ex) {
            String err = summarizeError(ex);
            log.error("Fallo action job {}: {}", action.actionName, err, ex);

            job.setStatus("FAILED");
            job.setErrorMessage(err);
            job.setOutputLog(trimOutput(output.toString()));
            job.setFinishedAt(Instant.now());
            repository.update(job);
        }
    }

    private List<List<String>> buildCommandPlan(ActionType action) {
        Path scriptsDir = workspaceDir.resolve("automation/scripts");
        Path seedScript = scriptsDir.resolve("seed_demo_data.sh");
        Path backupScript = scriptsDir.resolve("backup_db.sh");
        Path restoreScript = scriptsDir.resolve("restore_db.sh");
        Path smokeScript = scriptsDir.resolve("smoke_test_restore.sh");

        ensureReadableExecutable(seedScript);
        ensureReadableExecutable(backupScript);
        ensureReadableExecutable(restoreScript);
        ensureReadableExecutable(smokeScript);

        return switch (action) {
            case RUN_SCAN -> List.of(List.of(
                "bash",
                seedScript.toString(),
                "--host", hostName,
                "--api-base", apiBaseUrl,
                "--target-base", targetBaseUrl,
                "--no-reset"
            ));
            case RUN_BACKUP -> List.of(List.of("bash", backupScript.toString(), hostName));
            case RUN_RESTORE -> List.of(
                List.of("bash", restoreScript.toString()),
                List.of("bash", smokeScript.toString())
            );
        };
    }

    private ExecutionResult execute(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workspaceDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLimited(output, line + "\n");
            }
        }

        int exitCode = process.waitFor();
        return new ExecutionResult(exitCode, output.toString());
    }

    private Path resolveWorkspaceDir(String configuredWorkspaceDir) {
        if (configuredWorkspaceDir != null && !configuredWorkspaceDir.isBlank()) {
            Path configured = Paths.get(configuredWorkspaceDir).toAbsolutePath().normalize();
            if (!Files.exists(configured.resolve("compose/docker-compose.yml"))) {
                throw new IllegalStateException("Workspace configurado invalido: " + configured);
            }
            return configured;
        }

        Path local = Paths.get("").toAbsolutePath().normalize();
        if (Files.exists(local.resolve("compose/docker-compose.yml"))) {
            return local;
        }

        Path mounted = Paths.get("/workspace").toAbsolutePath().normalize();
        if (Files.exists(mounted.resolve("compose/docker-compose.yml"))) {
            return mounted;
        }

        throw new IllegalStateException("No se pudo resolver el workspace para ejecutar acciones seguras.");
    }

    private void ensureReadableExecutable(Path script) {
        if (!Files.exists(script)) {
            throw new IllegalStateException("No existe script permitido: " + script);
        }
        if (!Files.isReadable(script)) {
            throw new IllegalStateException("No se puede leer script permitido: " + script);
        }
    }

    private String summarizeError(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + msg;
    }

    private String toDisplayCommand(List<String> command) {
        List<String> quoted = new ArrayList<>(command.size());
        for (String arg : command) {
            if (arg.contains(" ")) {
                quoted.add('"' + arg + '"');
            } else {
                quoted.add(arg);
            }
        }
        return String.join(" ", quoted);
    }

    private void appendLimited(StringBuilder sb, String text) {
        if (sb.length() >= OUTPUT_LIMIT) {
            return;
        }
        int remaining = OUTPUT_LIMIT - sb.length();
        if (text.length() <= remaining) {
            sb.append(text);
            return;
        }
        sb.append(text, 0, remaining);
        sb.append("\n...[output truncated]...");
    }

    private String trimOutput(String output) {
        if (output == null) {
            return null;
        }
        return output.length() <= OUTPUT_LIMIT ? output : output.substring(0, OUTPUT_LIMIT);
    }

    @PreDestroy
    void shutdownExecutor() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    private enum ActionType {
        RUN_SCAN("run-scan"),
        RUN_BACKUP("run-backup"),
        RUN_RESTORE("run-restore");

        private final String actionName;

        ActionType(String actionName) {
            this.actionName = actionName;
        }
    }

    private static class ExecutionResult {
        private final int exitCode;
        private final String output;

        private ExecutionResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
