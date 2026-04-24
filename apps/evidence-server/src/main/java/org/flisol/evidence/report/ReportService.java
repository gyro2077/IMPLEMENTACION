package org.flisol.evidence.report;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.flisol.evidence.compliance.ComplianceScanService;
import org.flisol.evidence.read.EvidenceReadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final String TEMPLATE_PATH = "reports/evidence_report.jrxml";

    private final ReportJobRepository reportJobRepository;
    private final EvidenceReadRepository evidenceReadRepository;
    private final ComplianceScanService complianceScanService;
    private final Path outputDir;
    private volatile JasperReport compiledReport;

    public ReportService(ReportJobRepository reportJobRepository,
                         EvidenceReadRepository evidenceReadRepository,
                         ComplianceScanService complianceScanService,
                         @Value("${app.reports.output-dir:/app/data/reports}") String outputDir) {
        this.reportJobRepository = reportJobRepository;
        this.evidenceReadRepository = evidenceReadRepository;
        this.complianceScanService = complianceScanService;
        this.outputDir = Paths.get(outputDir).toAbsolutePath().normalize();
    }

    public ReportJob generateReport(String hostName) {
        if (hostName == null || hostName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host requerido para generar reporte.");
        }

        UUID jobId = UUID.randomUUID();
        ReportJob job = new ReportJob(jobId, hostName, "PENDING");
        reportJobRepository.save(job);

        // Flujo sincronico para mantener MVP simple y depurable.
        job.setStartedAt(Instant.now());
        job.setStatus("RUNNING");
        reportJobRepository.update(job);

        try {
            Map<String, Object> hostData = findHostData(hostName);
            JasperReport report = getOrCompileTemplate();

            String scanIdStr = asString(hostData.get("latest_compliance_scan_id"));
            String criticalFindings = "Sin hallazgos.";
            if (scanIdStr != null) {
                Map<String, Object> scanData = complianceScanService.getScanById(UUID.fromString(scanIdStr));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> topFailed = (List<Map<String, Object>>) scanData.get("top_failed_rules");
                if (topFailed != null && !topFailed.isEmpty()) {
                    criticalFindings = topFailed.stream()
                        .map(r -> "- [" + r.get("severity") + "] " + r.get("rule_id") + ": " + r.get("title"))
                        .collect(Collectors.joining("\n"));
                }
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("hostName", hostName);
            parameters.put("generationDate", Instant.now().toString());
            parameters.put("complianceScore", asString(hostData.get("latest_compliance_score"), "N/A"));
            parameters.put("criticalFindings", criticalFindings);
            parameters.put("lastBackupLabel", asString(hostData.get("latest_backup_label"), "N/A"));
            parameters.put("lastRestoreRto", asString(hostData.get("latest_restore_rto_seconds"), "N/A") + " seg");
            parameters.put("executiveConclusion", calculateConclusion(hostData));

            JasperPrint jasperPrint = JasperFillManager.fillReport(report, parameters, new JREmptyDataSource());

            Files.createDirectories(outputDir);
            Path pdfPath = outputDir.resolve("evidence_" + sanitize(hostName) + "_" + jobId + ".pdf").toAbsolutePath();
            
            JasperExportManager.exportReportToPdfFile(jasperPrint, pdfPath.toString());

            job.setPdfPath(pdfPath.toString());
            job.setStatus("COMPLETED");
            job.setFinishedAt(Instant.now());
            reportJobRepository.update(job);
            return job;
        } catch (ResponseStatusException ex) {
            markFailed(job, ex);
            throw ex;
        } catch (Exception ex) {
            markFailed(job, ex);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "No fue posible generar el reporte: " + summarizeRootCause(ex),
                ex
            );
        }
    }

    public Map<String, Object> getJobStatus(UUID jobId) {
        Map<String, Object> job = reportJobRepository.findById(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job no encontrado: " + jobId);
        }
        return job;
    }

    private void markFailed(ReportJob job, Exception ex) {
        String detailed = buildDetailedErrorMessage(ex);
        log.error("Fallo al generar reporte. host={} jobId={} cause={}", job.getHostName(), job.getId(), detailed, ex);

        job.setStatus("FAILED");
        job.setErrorMessage(detailed);
        job.setFinishedAt(Instant.now());
        reportJobRepository.update(job);
    }

    private Map<String, Object> findHostData(String hostName) {
        List<Map<String, Object>> hosts = evidenceReadRepository.listHostsWithLatestEvidence();
        return hosts.stream()
            .filter(h -> hostName.equals(h.get("host")))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Host sin evidencia: " + hostName));
    }

    private JasperReport getOrCompileTemplate() throws Exception {
        if (compiledReport != null) {
            return compiledReport;
        }

        synchronized (this) {
            if (compiledReport != null) {
                return compiledReport;
            }

            ClassPathResource template = new ClassPathResource(TEMPLATE_PATH);
            if (!template.exists()) {
                throw new IllegalStateException("Template no encontrado en classpath: " + TEMPLATE_PATH);
            }

            try (InputStream templateStream = template.getInputStream()) {
                compiledReport = JasperCompileManager.compileReport(templateStream);
                return compiledReport;
            }
        }
    }

    private String buildDetailedErrorMessage(Throwable ex) {
        String chain = summarizeExceptionChain(ex);
        if (chain.length() <= 1800) {
            return chain;
        }
        return chain.substring(0, 1800);
    }

    private String summarizeExceptionChain(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 6) {
            if (depth > 0) {
                sb.append(" | caused by: ");
            }
            sb.append(current.getClass().getSimpleName());
            String msg = current.getMessage();
            if (msg != null && !msg.isBlank()) {
                sb.append(" - ").append(msg);
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }

    private String summarizeRootCause(Throwable ex) {
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

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String calculateConclusion(Map<String, Object> hostData) {
        boolean hasCompliance = hostData.get("latest_compliance_scan_id") != null;
        boolean hasBackup = hostData.get("latest_backup_label") != null;
        boolean hasRestore = hostData.get("latest_restore_backup_label") != null;

        if (hasCompliance && hasBackup && hasRestore) {
            return "El host cuenta con un nivel adecuado de cumplimiento y validacion de resiliencia (backup y restore exitosos). Cumple con los requisitos de Evidence as Code.";
        } else if (hasCompliance || hasBackup || hasRestore) {
            return "El host tiene evidencia parcial. Se requiere completar las validaciones de cumplimiento y/o resiliencia.";
        }
        return "El host no cuenta con evidencia registrada.";
    }

    public List<Map<String, Object>> listRecentReports(int limit) {
        return reportJobRepository.findRecent(limit);
    }

    private String asString(Object obj) {
        if (obj == null) {
            return null;
        }
        String text = obj.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String asString(Object obj, String fallback) {
        String value = asString(obj);
        return value == null ? fallback : value;
    }
}
