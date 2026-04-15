package org.flisol.evidence.compliance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ComplianceScanService {

    private final JdbcTemplate jdbcTemplate;

    public ComplianceScanService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> importScan(Map<String, Object> payload) {
        UUID scanId = UUID.randomUUID();

        String host = asString(payload.get("host"), "target-local");
        String profileId = asString(payload.get("profile_id"), "unknown");
        BigDecimal score = asBigDecimal(payload.get("score"));
        String status = asString(payload.get("status"), "completed");
        String arfPath = asString(payload.get("arf_path"), "");
        String htmlPath = asString(payload.get("html_report_path"), "");
        OffsetDateTime startedAt = asDateTime(payload.get("started_at"));
        OffsetDateTime finishedAt = asDateTime(payload.get("finished_at"));

        jdbcTemplate.update(
            """
            INSERT INTO compliance_scan (
              id, host_name, profile_id, score, status,
              arf_path, html_report_path, started_at, finished_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            scanId,
            host,
            profileId,
            score,
            status,
            arfPath,
            htmlPath,
            startedAt,
            finishedAt
        );

        List<Map<String, Object>> failedRules = asMapList(payload.get("failed_rules"));
        int inserted = 0;
        for (Map<String, Object> rule : failedRules) {
            UUID ruleId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                INSERT INTO compliance_rule_result (id, scan_id, rule_id, severity, result, title)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                ruleId,
                scanId,
                asString(rule.get("rule_id"), "unknown"),
                asString(rule.get("severity"), "unknown"),
                asString(rule.get("result"), "fail"),
                asString(rule.get("title"), "")
            );
            inserted++;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scan_id", scanId.toString());
        response.put("failed_rules_saved", inserted);
        response.put("status", "stored");
        return response;
    }

    public List<Map<String, Object>> listScans() {
        List<Map<String, Object>> scans = jdbcTemplate.query(
            """
            SELECT id, host_name, profile_id, score, status, arf_path, html_report_path, started_at, finished_at, created_at
            FROM compliance_scan
            ORDER BY created_at DESC
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getObject("id").toString());
                row.put("host", rs.getString("host_name"));
                row.put("profile_id", rs.getString("profile_id"));
                row.put("score", rs.getBigDecimal("score"));
                row.put("status", rs.getString("status"));
                row.put("arf_path", rs.getString("arf_path"));
                row.put("html_report_path", rs.getString("html_report_path"));
                row.put("started_at", asIso(rs.getObject("started_at")));
                row.put("finished_at", asIso(rs.getObject("finished_at")));
                row.put("created_at", asIso(rs.getObject("created_at")));
                return row;
            }
        );

        for (Map<String, Object> scan : scans) {
            String scanId = scan.get("id").toString();
            List<Map<String, Object>> topFailed = jdbcTemplate.query(
                """
                SELECT rule_id, severity, title
                FROM compliance_rule_result
                WHERE scan_id = ? AND LOWER(COALESCE(result, '')) = 'fail'
                ORDER BY
                  CASE LOWER(COALESCE(severity, ''))
                    WHEN 'critical' THEN 1
                    WHEN 'high' THEN 2
                    WHEN 'medium' THEN 3
                    WHEN 'low' THEN 4
                    ELSE 5
                  END,
                  title
                LIMIT 10
                """,
                (rs, rowNum) -> {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("rule_id", rs.getString("rule_id"));
                    rule.put("severity", rs.getString("severity"));
                    rule.put("title", rs.getString("title"));
                    return rule;
                },
                UUID.fromString(scanId)
            );
            scan.put("top_failed_rules", topFailed);
        }

        return scans;
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String txt = value.toString().trim();
        return txt.isEmpty() ? fallback : txt;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private OffsetDateTime asDateTime(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private String asIso(Object value) {
        return value == null ? null : value.toString();
    }
}
