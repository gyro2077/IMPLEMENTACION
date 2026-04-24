package org.flisol.evidence.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flisol.evidence.read.EvidenceReadService;
import org.flisol.evidence.report.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API JSON dedicada para alimentar el Dashboard Administrador.
 *
 * Patrón para acciones seguras futuras (ETAPA 2):
 * -----------------------------------------------
 * Cada acción controlable desde UI debe seguir este contrato:
 * 1. Endpoint backend dedicado (POST /api/actions/{action-name})
 * 2. Whitelist de acciones permitidas (enum o tabla)
 * 3. Ejecución asíncrona con persistencia de estado (tabla action_job)
 * 4. Respuesta inmediata con job_id
 * 5. Polling desde UI: GET /api/actions/{job_id}/status
 * 6. NUNCA exponer shell arbitrario por HTTP
 *
 * Para habilitar backup/restore/scan desde UI:
 * - Crear ActionJobService con whitelist de comandos pre-aprobados
 * - Cada acción ejecuta un script predefinido, no input del usuario
 * - El job persiste en DB con estado PENDING→RUNNING→COMPLETED|FAILED
 * - El dashboard.js hace polling del estado y actualiza el toast
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final EvidenceReadService evidenceReadService;
    private final ReportService reportService;

    public DashboardApiController(EvidenceReadService evidenceReadService, ReportService reportService) {
        this.evidenceReadService = evidenceReadService;
        this.reportService = reportService;
    }

    @GetMapping("/data")
    public Map<String, Object> getData() {
        List<Map<String, Object>> hosts = evidenceReadService.listHosts();
        List<Map<String, Object>> scans = evidenceReadService.listScans();
        List<Map<String, Object>> backups = evidenceReadService.listBackups();
        List<Map<String, Object>> restores = evidenceReadService.listRestores();
        List<Map<String, Object>> reports = reportService.listRecentReports(10);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generated_at", Instant.now().toString());
        data.put("summary", buildSummary(hosts));
        data.put("primary_host", findPrimaryHost(hosts));
        data.put("hosts", hosts);
        data.put("scans", scans);
        data.put("backups", backups);
        data.put("restores", restores);
        data.put("reports", reports);
        data.put("timeline", buildTimeline(scans, backups, restores, reports));
        data.put("actions", buildActions(hosts));
        return data;
    }

    private Map<String, Object> buildSummary(List<Map<String, Object>> hosts) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("total_hosts", hosts.size());
        int ready = 0, partial = 0, problem = 0, empty = 0;
        for (Map<String, Object> h : hosts) {
            String status = str(h.get("evidence_status"));
            switch (status) {
                case "ready" -> ready++;
                case "partial" -> partial++;
                case "problem" -> problem++;
                default -> empty++;
            }
        }
        s.put("ready", ready);
        s.put("partial", partial);
        s.put("problem", problem);
        s.put("empty", empty);
        return s;
    }

    private Map<String, Object> findPrimaryHost(List<Map<String, Object>> hosts) {
        for (Map<String, Object> h : hosts) {
            if ("target-cachyos".equalsIgnoreCase(str(h.get("host")))) {
                return h;
            }
        }
        if (!hosts.isEmpty()) {
            return hosts.getFirst();
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("host", "sin-host");
        empty.put("evidence_status", "empty");
        empty.put("evidence_status_label", "Sin evidencia");
        empty.put("evidence_status_message", "No hay datos cargados.");
        return empty;
    }

    private List<Map<String, Object>> buildTimeline(
            List<Map<String, Object>> scans,
            List<Map<String, Object>> backups,
            List<Map<String, Object>> restores,
            List<Map<String, Object>> reports) {

        List<Map<String, Object>> timeline = new ArrayList<>();

        if (scans != null) {
            for (Map<String, Object> s : scans) {
                timeline.add(event("scan", "Escaneo de Cumplimiento",
                    str(s.get("host")), "Score: " + str(s.get("score")), str(s.get("created_at"))));
            }
        }
        if (backups != null) {
            for (Map<String, Object> b : backups) {
                timeline.add(event("backup", "Backup " + str(b.get("status")),
                    str(b.get("host")), "Label: " + str(b.get("backup_label")), str(b.get("created_at"))));
            }
        }
        if (restores != null) {
            for (Map<String, Object> r : restores) {
                timeline.add(event("restore", "Restore " + str(r.get("status")),
                    str(r.get("host")), "RTO: " + str(r.get("rto_seconds")) + "s", str(r.get("created_at"))));
            }
        }
        if (reports != null) {
            for (Map<String, Object> rep : reports) {
                timeline.add(event("report", "Reporte PDF " + str(rep.get("status")),
                    str(rep.get("host_name")), "Job: " + shortId(str(rep.get("id"))), str(rep.get("created_at"))));
            }
        }

        timeline.sort(Comparator.comparing((Map<String, Object> e) -> str(e.get("time"))).reversed());
        return timeline.size() > 8 ? timeline.subList(0, 8) : timeline;
    }

    private Map<String, Object> event(String type, String title, String host, String details, String time) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", type);
        e.put("title", title);
        e.put("host", host);
        e.put("details", details);
        e.put("time", time);
        return e;
    }

    private Map<String, Object> buildActions(List<Map<String, Object>> hosts) {
        String primaryHost = "target-cachyos";
        for (Map<String, Object> h : hosts) {
            if ("target-cachyos".equalsIgnoreCase(str(h.get("host")))) {
                primaryHost = str(h.get("host"));
                break;
            }
        }

        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("generate_report", Map.of(
            "enabled", true,
            "label", "Generar Reporte PDF",
            "endpoint", "/api/reports/evidence/" + primaryHost,
            "method", "POST"
        ));
        actions.put("open_grafana", Map.of(
            "enabled", true, "label", "Abrir Grafana", "url", "http://localhost:3000"
        ));
        actions.put("open_jaeger", Map.of(
            "enabled", true, "label", "Abrir Jaeger", "url", "http://localhost:16686"
        ));
        actions.put("reload_dataset", Map.of(
            "enabled", true, "label", "Recargar Dataset",
            "endpoint", "/api/actions/reload-dataset", "method", "POST"
        ));
        actions.put("run_scan", Map.of(
            "enabled", true,
            "label", "Ejecutar Scan",
            "endpoint", "/api/actions/run-scan",
            "method", "POST"
        ));
        actions.put("run_backup", Map.of(
            "enabled", true,
            "label", "Ejecutar Backup",
            "endpoint", "/api/actions/run-backup",
            "method", "POST"
        ));
        actions.put("run_restore", Map.of(
            "enabled", true,
            "label", "Ejecutar Restore",
            "endpoint", "/api/actions/run-restore",
            "method", "POST"
        ));
        return actions;
    }

    private String str(Object v) {
        return v == null ? "-" : v.toString();
    }

    private String shortId(String text) {
        if (text == null || "-".equals(text)) return "-";
        return text.length() <= 8 ? text : text.substring(0, 8);
    }
}
