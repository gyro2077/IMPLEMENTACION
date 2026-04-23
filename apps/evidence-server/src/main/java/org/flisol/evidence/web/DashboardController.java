package org.flisol.evidence.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flisol.evidence.read.EvidenceReadService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final EvidenceReadService evidenceReadService;

    public DashboardController(EvidenceReadService evidenceReadService) {
        this.evidenceReadService = evidenceReadService;
    }

    @GetMapping(value = {"/", "/dashboard"}, produces = MediaType.TEXT_HTML_VALUE)
    public String dashboard() {
        List<Map<String, Object>> hosts = evidenceReadService.listHosts();
        List<Map<String, Object>> scans = evidenceReadService.listScans();
        List<Map<String, Object>> backups = evidenceReadService.listBackups();
        List<Map<String, Object>> restores = evidenceReadService.listRestores();

        List<Map<String, Object>> hostRows = hosts.stream().map(this::toHostView).toList();
        Map<String, Integer> summary = summarize(hostRows);
        Map<String, Object> primaryHost = pickPrimaryHost(hostRows);

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"es\"><head>");
        html.append("<meta charset=\"utf-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        html.append("<title>Estado de Auditoría y Cumplimiento - FLISOL</title>");
        html.append("<link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">");
        html.append("<style>");
        html.append(":root { --bg: #f8fafc; --surface: #ffffff; --text: #0f172a; --text-muted: #64748b; ");
        html.append("--ready-bg: #dcfce7; --ready-text: #166534; --ready-border: #bbf7d0; ");
        html.append("--partial-bg: #fef08a; --partial-text: #854d0e; --partial-border: #fde047; ");
        html.append("--problem-bg: #fee2e2; --problem-text: #991b1b; --problem-border: #fecaca; ");
        html.append("--empty-bg: #f1f5f9; --empty-text: #334155; --empty-border: #e2e8f0; }");
        html.append("body { font-family: 'Inter', sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 2rem; line-height: 1.5; -webkit-font-smoothing: antialiased; }");
        html.append(".container { max-width: 1200px; margin: 0 auto; }");
        html.append("h1 { font-size: 2.25rem; font-weight: 800; letter-spacing: -0.025em; margin: 0 0 0.5rem 0; color: #0f172a; }");
        html.append("h2 { font-size: 1.5rem; font-weight: 700; margin: 0 0 1rem 0; letter-spacing: -0.025em; }");
        html.append("h3 { font-size: 1.25rem; font-weight: 600; margin: 0 0 0.5rem 0; }");
        html.append(".top { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem; margin-bottom: 2rem; }");
        html.append(".subtitle { color: var(--text-muted); font-size: 1.125rem; max-width: 800px; margin: 0; }");
        html.append(".muted { color: var(--text-muted); font-size: 0.875rem; }");
        html.append(".grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1.5rem; margin-bottom: 2rem; }");
        html.append(".metric { background: var(--surface); border-radius: 1rem; padding: 1.5rem; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05), 0 2px 4px -2px rgba(0,0,0,0.05); border: 1px solid #e2e8f0; transition: transform 0.2s; display: flex; flex-direction: column; align-items: flex-start; justify-content: center; }");
        html.append(".metric:hover { transform: translateY(-2px); box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1); }");
        html.append(".metric .label { font-size: 0.875rem; text-transform: uppercase; letter-spacing: 0.05em; font-weight: 600; margin-bottom: 0.5rem; }");
        html.append(".metric .value { font-size: 2.5rem; font-weight: 800; line-height: 1; }");
        html.append(".state-ready { background: var(--ready-bg); color: var(--ready-text); border-color: var(--ready-border); }");
        html.append(".state-partial { background: var(--partial-bg); color: var(--partial-text); border-color: var(--partial-border); }");
        html.append(".state-problem { background: var(--problem-bg); color: var(--problem-text); border-color: var(--problem-border); }");
        html.append(".state-empty { background: var(--empty-bg); color: var(--empty-text); border-color: var(--empty-border); }");
        html.append(".card { background: var(--surface); border-radius: 1rem; padding: 2rem; margin-bottom: 2rem; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.05), 0 4px 6px -4px rgba(0,0,0,0.05); border: 1px solid #e2e8f0; }");
        html.append(".card.primary-host { border: 2px solid #3b82f6; position: relative; overflow: hidden; }");
        html.append(".card.primary-host::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 6px; background: linear-gradient(90deg, #3b82f6, #8b5cf6); }");
        html.append(".pill { display: inline-flex; align-items: center; padding: 0.375rem 0.875rem; border-radius: 9999px; font-size: 0.875rem; font-weight: 600; border: 1px solid transparent; }");
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 1rem; }");
        html.append("th, td { padding: 1rem; text-align: left; border-bottom: 1px solid #e2e8f0; }");
        html.append("th { font-weight: 600; color: var(--text-muted); text-transform: uppercase; font-size: 0.75rem; letter-spacing: 0.05em; background: #f8fafc; }");
        html.append("tbody tr:hover { background: #f8fafc; }");
        html.append(".legend-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1rem; margin-top: 1rem; }");
        html.append(".legend-item { display: flex; align-items: flex-start; gap: 1rem; padding: 1rem; background: var(--bg); border-radius: 0.75rem; border: 1px solid #e2e8f0; }");
        html.append(".uuid { font-family: ui-monospace, monospace; font-size: 0.75rem; color: #94a3b8; letter-spacing: -0.025em; }");
        html.append(".kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-top: 1.5rem; }");
        html.append(".kpi-item { padding: 1rem; background: #f8fafc; border-radius: 0.5rem; border: 1px solid #e2e8f0; }");
        html.append(".kpi-label { font-size: 0.75rem; text-transform: uppercase; font-weight: 600; color: var(--text-muted); margin-bottom: 0.25rem; }");
        html.append(".kpi-value { font-size: 1.125rem; font-weight: 700; color: var(--text); }");
        html.append(".links a { margin-right: 1rem; color: #3b82f6; text-decoration: none; font-weight: 500; }");
        html.append(".links a:hover { text-decoration: underline; }");
        html.append("</style></head><body>");
        html.append("<div class=\"container\">");

        html.append("<div class=\"top\"><div><h1>Panel de Estado Operativo (Demo)</h1>");
        html.append("<p class=\"subtitle\">Visión general del cumplimiento normativo, respaldos automáticos y recuperación ante desastres.</p></div>");
        html.append("<div style=\"text-align: right;\"><div class=\"pill state-ready\">Entorno de Demostración</div><div class=\"muted\" style=\"margin-top: 0.5rem;\">Generado el: ").append(escape(Instant.now().toString())).append("</div></div></div>");

        html.append("<div class=\"grid\">");
        html.append(metricCard("Equipos Auditados", summary.get("all"), "state-empty"));
        html.append(metricCard("Auditoría Exitosa", summary.get("ready"), "state-ready"));
        html.append(metricCard("Revisión Parcial", summary.get("partial"), "state-partial"));
        html.append(metricCard("Atención Requerida", summary.get("problem"), "state-problem"));
        html.append("</div>");

        html.append("<div class=\"card primary-host\"><h2>Servidor Principal de la Demo</h2>");
        html.append("<h3>").append(escape(asText(primaryHost.get("host")))).append("</h3>");
        html.append("<div style=\"margin-bottom: 1.5rem; margin-top: 0.5rem;\"><span class=\"pill ").append(cssClassForState(asText(primaryHost.get("status_key")))).append("\">")
            .append(escape(asText(primaryHost.get("status_label")))).append("</span></div>");
        html.append("<p style=\"font-size: 1.125rem; color: #334155; margin-bottom: 1.5rem;\">").append(escape(asText(primaryHost.get("status_message")))).append("</p>");
        
        html.append("<div class=\"kpi-grid\">");
        html.append("<div class=\"kpi-item\"><div class=\"kpi-label\">Puntaje de Seguridad</div><div class=\"kpi-value\">").append(escape(asText(primaryHost.get("compliance_human")))).append("</div></div>");
        html.append("<div class=\"kpi-item\"><div class=\"kpi-label\">Último Respaldo</div><div class=\"kpi-value\">").append(escape(asText(primaryHost.get("backup_human")))).append("</div></div>");
        html.append("<div class=\"kpi-item\"><div class=\"kpi-label\">Prueba de Recuperación</div><div class=\"kpi-value\">").append(escape(asText(primaryHost.get("restore_human")))).append("</div></div>");
        html.append("</div></div>");

        html.append("<div class=\"card\"><h2>Guía Rápida de Estados</h2>");
        html.append("<p class=\"muted\" style=\"margin-bottom: 1rem;\">Un resumen sencillo para comprender la salud de cada servidor en nuestra infraestructura.</p>");
        html.append("<div class=\"legend-grid\">");
        html.append("<div class=\"legend-item\"><div><span class=\"pill state-ready\">Listo (Verde)</span></div><div class=\"muted\"><strong>Protección Total:</strong> El servidor aprobó los escaneos de seguridad, tiene respaldos exitosos y verificamos que esos respaldos se pueden restaurar sin problemas.</div></div>");
        html.append("<div class=\"legend-item\"><div><span class=\"pill state-partial\">Parcial (Amarillo)</span></div><div class=\"muted\"><strong>En progreso:</strong> El servidor está integrado pero falta completar un paso crítico, como validar un respaldo.</div></div>");
        html.append("<div class=\"legend-item\"><div><span class=\"pill state-problem\">Problema (Rojo)</span></div><div class=\"muted\"><strong>Alerta Operativa:</strong> Riesgos detectados. Puede ser por escaso puntaje de cumplimiento, fallos en el respaldo, o fallos durante la simulación de recuperación.</div></div>");
        html.append("<div class=\"legend-item\"><div><span class=\"pill state-empty\">Sin Datos (Gris)</span></div><div class=\"muted\"><strong>Pendiente:</strong> El servidor aún no cuenta con historial operativo registrado en el sistema.</div></div>");
        html.append("</div></div>");

        html.append("<div class=\"card\"><h2>Inventario General</h2>");
        html.append("<table><thead><tr><th>Servidor</th><th>Estado Visual</th><th>Cumplimiento</th><th>Respaldo</th><th>Recuperación (RTO)</th></tr></thead><tbody>");
        for (Map<String, Object> host : hostRows) {
            html.append("<tr>");
            html.append("<td><strong>").append(escape(asText(host.get("host")))).append("</strong></td>");
            html.append("<td><span class=\"pill ").append(cssClassForState(asText(host.get("status_key")))).append("\">")
                .append(escape(asText(host.get("status_label")))).append("</span><br/><span class=\"muted\" style=\"display:block; margin-top:0.25rem;\">")
                .append(escape(asText(host.get("status_message")))).append("</span></td>");
            html.append("<td>").append(escape(asText(host.get("compliance_human")))).append("<br/><span class=\"muted\">")
                .append(escape(asText(host.get("latest_compliance_at")))).append("</span></td>");
            html.append("<td>").append(escape(asText(host.get("backup_human")))).append("<br/><span class=\"muted\">")
                .append(escape(asText(host.get("latest_backup_at")))).append("</span></td>");
            html.append("<td>").append(escape(asText(host.get("restore_human")))).append("<br/><span class=\"muted\">")
                .append(escape(asText(host.get("latest_restore_at")))).append("</span></td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("<div class=\"card\"><h2>Últimas Auditorías de Seguridad</h2>");
        html.append("<table><thead><tr><th>Servidor</th><th>Puntaje</th><th>Política Base</th><th>Hallazgos</th><th>Estado</th></tr></thead><tbody>");
        for (Map<String, Object> scan : limit(scans, 10)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> topFailed = (List<Map<String, Object>>) scan.get("top_failed_rules");
            int findings = topFailed == null ? 0 : topFailed.size();
            html.append("<tr>");
            html.append("<td>").append(escape(asText(scan.get("host")))).append("</td>");
            html.append("<td><strong>").append(escape(asText(scan.get("score")))).append("</strong></td>");
            html.append("<td>").append(escape(asText(scan.get("profile_id")))).append("</td>");
            html.append("<td>").append(findings).append(" reglas fallidas</td>");
            html.append("<td>").append(escape(asText(scan.get("status")))).append("<br/><span class=\"uuid\">Ref: ")
                .append(shortId(asText(scan.get("id")))).append("</span></td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("<div class=\"card\"><h2>Historial de Respaldos (Backups)</h2>");
        html.append("<table><thead><tr><th>Servidor</th><th>Etiqueta</th><th>Estado</th><th>Tamaño</th><th>Duración</th></tr></thead><tbody>");
        for (Map<String, Object> backup : limit(backups, 10)) {
            html.append("<tr>");
            html.append("<td>").append(escape(asText(backup.get("host")))).append("</td>");
            html.append("<td>").append(escape(asText(backup.get("backup_label")))).append("</td>");
            html.append("<td><span class=\"pill ").append(asText(backup.get("status")).contains("fail") ? "state-problem" : "state-ready").append("\">")
                .append(escape(asText(backup.get("status")))).append("</span></td>");
            html.append("<td>").append(escape(asText(backup.get("size_bytes")))).append(" bytes</td>");
            html.append("<td>").append(escape(asText(backup.get("duration_seconds")))).append(" segundos</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("<div class=\"card\"><h2>Historial de Recuperaciones (Restores)</h2>");
        html.append("<table><thead><tr><th>Servidor</th><th>Etiqueta</th><th>Estado</th><th>Tiempo RTO</th><th>Pruebas E2E</th><th>Filas Recuperadas</th></tr></thead><tbody>");
        for (Map<String, Object> restore : limit(restores, 10)) {
            html.append("<tr>");
            html.append("<td>").append(escape(asText(restore.get("host")))).append("</td>");
            html.append("<td>").append(escape(asText(restore.get("backup_label")))).append("</td>");
            html.append("<td><span class=\"pill ").append(asText(restore.get("status")).contains("fail") ? "state-problem" : "state-ready").append("\">")
                .append(escape(asText(restore.get("status")))).append("</span></td>");
            html.append("<td>").append(escape(asText(restore.get("rto_seconds")))).append(" segundos</td>");
            html.append("<td>").append(escape(asText(restore.get("smoke_passed")))).append(" de ")
                .append(escape(asText(restore.get("smoke_total")))).append("</td>");
            html.append("<td>").append(escape(asText(restore.get("rows_restored")))).append(" de ")
                .append(escape(asText(restore.get("rows_expected")))).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("<div class=\"card links\"><strong>Explorar APIs Crudas:</strong> ");
        html.append("<a href=\"/api/hosts\">Hosts</a>");
        html.append("<a href=\"/api/compliance/scans\">Scans</a>");
        html.append("<a href=\"/api/backups\">Backups</a>");
        html.append("<a href=\"/api/restores\">Restores</a>");
        html.append("<a href=\"/api/evidence/overview\">Resumen</a>");
        html.append("</div>");

        html.append("</div></body></html>");
        return html.toString();
    }

    private String metricCard(String label, int value, String cssClass) {
        return "<div class=\"metric " + cssClass + "\"><div class=\"label\">" + escape(label)
            + "</div><div class=\"value\">" + value + "</div></div>";
    }

    private Map<String, Integer> summarize(List<Map<String, Object>> hostRows) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("all", hostRows.size());
        summary.put("ready", 0);
        summary.put("partial", 0);
        summary.put("problem", 0);
        summary.put("empty", 0);
        for (Map<String, Object> host : hostRows) {
            String key = asText(host.get("status_key"));
            if (summary.containsKey(key)) {
                summary.put(key, summary.get(key) + 1);
            }
        }
        return summary;
    }

    private Map<String, Object> pickPrimaryHost(List<Map<String, Object>> hostRows) {
        for (Map<String, Object> host : hostRows) {
            if ("target-cachyos".equalsIgnoreCase(asText(host.get("host")))) {
                return host;
            }
        }
        if (hostRows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("host", "sin-host");
            empty.put("status_key", "empty");
            empty.put("status_label", "Sin evidencia");
            empty.put("status_message", "No hay datos cargados aun para mostrar.");
            empty.put("compliance_human", "No disponible");
            empty.put("backup_human", "No disponible");
            empty.put("restore_human", "No disponible");
            return empty;
        }
        return hostRows.getFirst();
    }

    private Map<String, Object> toHostView(Map<String, Object> host) {
        Map<String, Object> view = new LinkedHashMap<>(host);

        double score = parseDouble(host.get("latest_compliance_score"));
        boolean hasCompliance = host.get("latest_compliance_scan_id") != null;
        boolean hasBackup = host.get("latest_backup_label") != null;
        boolean hasRestore = host.get("latest_restore_backup_label") != null;

        int rto = parseInt(host.get("latest_restore_rto_seconds"));

        view.put("status_key", asText(host.get("evidence_status")));
        view.put("status_label", asText(host.get("evidence_status_label")));
        view.put("status_message", asText(host.get("evidence_status_message")));
        
        view.put("compliance_human", hasCompliance
            ? "Puntaje " + (score <= 0 ? "N/A" : asText(host.get("latest_compliance_score")) + " / 100")
            : "No hay scan cargado");
        view.put("backup_human", hasBackup
            ? "Backup " + asText(host.get("latest_backup_label")) + " (" + asText(host.get("latest_backup_status")) + ")"
            : "No hay backup registrado");
        view.put("restore_human", hasRestore
            ? "Restore " + asText(host.get("latest_restore_backup_label")) + " (RTO " + (rto <= 0 ? "N/A" : rto) + " s)"
            : "No hay restore verificado");

        return view;
    }



    private String cssClassForState(String status) {
        return switch (status) {
            case "ready" -> "state-ready";
            case "partial" -> "state-partial";
            case "problem" -> "state-problem";
            default -> "state-empty";
        };
    }

    private List<Map<String, Object>> limit(List<Map<String, Object>> rows, int max) {
        if (rows.size() <= max) {
            return rows;
        }
        return rows.subList(0, max);
    }

    private int parseInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private double parseDouble(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String asText(Object value) {
        return value == null ? "-" : value.toString();
    }

    private String shortId(String text) {
        if (text == null) {
            return "-";
        }
        return text.length() <= 8 ? text : text.substring(0, 8);
    }

    private String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
