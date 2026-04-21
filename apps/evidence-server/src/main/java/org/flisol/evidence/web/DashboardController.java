package org.flisol.evidence.web;

import java.time.Instant;
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

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"es\"><head>");
        html.append("<meta charset=\"utf-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        html.append("<title>FLISOL Evidence Dashboard</title>");
        html.append("<style>");
        html.append("body{font-family:Segoe UI,Arial,sans-serif;background:#f7f9fc;color:#1d2433;margin:0;padding:20px;}");
        html.append("h1,h2{margin:0 0 10px 0;} .top{display:flex;justify-content:space-between;align-items:baseline;flex-wrap:wrap;gap:8px;}");
        html.append(".links a{margin-right:12px;color:#0b5ed7;text-decoration:none;} .links a:hover{text-decoration:underline;}");
        html.append(".card{background:#fff;border:1px solid #d9e2ef;border-radius:10px;padding:14px;margin-top:14px;box-shadow:0 1px 2px rgba(0,0,0,0.03);}");
        html.append("table{width:100%;border-collapse:collapse;font-size:14px;} th,td{border-bottom:1px solid #e8edf5;padding:8px;text-align:left;vertical-align:top;}");
        html.append("th{background:#f2f6fc;} .muted{color:#6a7486;font-size:12px;} .tag{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;}");
        html.append(".tag-ready{background:#d9fbe5;color:#0b6b2e;} .tag-partial{background:#fff4d6;color:#8a6200;} .tag-empty{background:#eceff4;color:#4b566b;}");
        html.append("</style></head><body>");

        html.append("<div class=\"top\"><h1>Evidence Server - Fase 5A</h1>");
        html.append("<div class=\"muted\">Generado: ").append(escape(Instant.now().toString())).append("</div></div>");

        html.append("<div class=\"card links\"><strong>API:</strong> ");
        html.append("<a href=\"/api/hosts\">/api/hosts</a>");
        html.append("<a href=\"/api/compliance/scans\">/api/compliance/scans</a>");
        html.append("<a href=\"/api/backups\">/api/backups</a>");
        html.append("<a href=\"/api/restores\">/api/restores</a>");
        html.append("<a href=\"/api/evidence/overview\">/api/evidence/overview</a>");
        html.append("</div>");

        html.append("<div class=\"card\"><h2>Hosts y estado de evidencia</h2>");
        html.append("<table><thead><tr><th>Host</th><th>Compliance reciente</th><th>Ultimo backup valido</th><th>Ultimo restore verificado</th><th>Estado</th></tr></thead><tbody>");
        for (Map<String, Object> host : hosts) {
            String status = asText(host.get("evidence_status"));
            html.append("<tr>");
            html.append("<td>").append(escape(asText(host.get("host")))).append("</td>");
            html.append("<td>score=").append(escape(asText(host.get("latest_compliance_score")))).append("<br/><span class=\"muted\">")
                .append(escape(asText(host.get("latest_compliance_at")))).append("</span></td>");
            html.append("<td>").append(escape(asText(host.get("latest_backup_label")))).append("<br/><span class=\"muted\">")
                .append(escape(asText(host.get("latest_backup_at")))).append("</span></td>");
            html.append("<td>").append(escape(asText(host.get("latest_restore_backup_label")))).append(" (RTO ")
                .append(escape(asText(host.get("latest_restore_rto_seconds")))).append("s)<br/><span class=\"muted\">")
                .append(escape(asText(host.get("latest_restore_at")))).append("</span></td>");
            html.append("<td><span class=\"tag ").append(tagClass(status)).append("\">")
                .append(escape(status)).append("</span></td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("<div class=\"card\"><h2>Scans de cumplimiento (ultimos 10)</h2>");
        html.append("<table><thead><tr><th>ID</th><th>Host</th><th>Profile</th><th>Score</th><th>Status</th></tr></thead><tbody>");
        for (Map<String, Object> scan : limit(scans, 10)) {
            html.append("<tr>");
            html.append("<td><a href=\"/api/compliance/scans/").append(escape(asText(scan.get("id")))).append("\">")
                .append(escape(asText(scan.get("id")))).append("</a></td>");
            html.append("<td>").append(escape(asText(scan.get("host")))).append("</td>");
            html.append("<td>").append(escape(asText(scan.get("profile_id")))).append("</td>");
            html.append("<td>").append(escape(asText(scan.get("score")))).append("</td>");
            html.append("<td>").append(escape(asText(scan.get("status")))).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("<div class=\"card\"><h2>Backups (ultimos 10)</h2>");
        html.append("<table><thead><tr><th>ID</th><th>Host</th><th>Label</th><th>Status</th><th>Tamano</th><th>Duracion(s)</th></tr></thead><tbody>");
        for (Map<String, Object> backup : limit(backups, 10)) {
            html.append("<tr>");
            html.append("<td>").append(escape(asText(backup.get("id")))).append("</td>");
            html.append("<td>").append(escape(asText(backup.get("host")))).append("</td>");
            html.append("<td>").append(escape(asText(backup.get("backup_label")))).append("</td>");
            html.append("<td>").append(escape(asText(backup.get("status")))).append("</td>");
            html.append("<td>").append(escape(asText(backup.get("size_bytes")))).append("</td>");
            html.append("<td>").append(escape(asText(backup.get("duration_seconds")))).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("<div class=\"card\"><h2>Restores (ultimos 10)</h2>");
        html.append("<table><thead><tr><th>ID</th><th>Host</th><th>Backup label</th><th>Status</th><th>Filas</th><th>RTO(s)</th><th>Smoke</th></tr></thead><tbody>");
        for (Map<String, Object> restore : limit(restores, 10)) {
            html.append("<tr>");
            html.append("<td>").append(escape(asText(restore.get("id")))).append("</td>");
            html.append("<td>").append(escape(asText(restore.get("host")))).append("</td>");
            html.append("<td>").append(escape(asText(restore.get("backup_label")))).append("</td>");
            html.append("<td>").append(escape(asText(restore.get("status")))).append("</td>");
            html.append("<td>").append(escape(asText(restore.get("rows_restored")))).append("/")
                .append(escape(asText(restore.get("rows_expected")))).append("</td>");
            html.append("<td>").append(escape(asText(restore.get("rto_seconds")))).append("</td>");
            html.append("<td>").append(escape(asText(restore.get("smoke_passed")))).append("/")
                .append(escape(asText(restore.get("smoke_total")))).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");

        html.append("</body></html>");
        return html.toString();
    }

    private String tagClass(String status) {
        return switch (status) {
            case "ready" -> "tag-ready";
            case "partial" -> "tag-partial";
            default -> "tag-empty";
        };
    }

    private List<Map<String, Object>> limit(List<Map<String, Object>> rows, int max) {
        if (rows.size() <= max) {
            return rows;
        }
        return rows.subList(0, max);
    }

    private String asText(Object value) {
        return value == null ? "-" : value.toString();
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
