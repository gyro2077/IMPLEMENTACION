package org.flisol.evidence.read;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EvidenceReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public EvidenceReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listHostsWithLatestEvidence() {
        return jdbcTemplate.query(
            """
            WITH hosts AS (
              SELECT host_name FROM compliance_scan
              UNION
              SELECT host_name FROM backup_run
              UNION
              SELECT host_name FROM restore_run
            )
            SELECT
              h.host_name,
              cs.id AS compliance_scan_id,
              cs.score AS compliance_score,
              cs.status AS compliance_status,
              COALESCE(cs.finished_at, cs.created_at) AS compliance_at,
              b.backup_label,
              b.status AS backup_status,
              b.size_bytes AS backup_size_bytes,
              COALESCE(b.finished_at, b.created_at) AS backup_at,
              r.backup_label AS restore_backup_label,
              r.status AS restore_status,
              r.rto_seconds,
              r.smoke_passed,
              r.smoke_total,
              COALESCE(r.finished_at, r.created_at) AS restore_at
            FROM hosts h
            LEFT JOIN LATERAL (
              SELECT id, score, status, finished_at, created_at
              FROM compliance_scan
              WHERE host_name = h.host_name
              ORDER BY COALESCE(finished_at, created_at) DESC
              LIMIT 1
            ) cs ON TRUE
            LEFT JOIN LATERAL (
              SELECT backup_label, status, size_bytes, finished_at, created_at
              FROM backup_run
              WHERE host_name = h.host_name AND LOWER(COALESCE(status, '')) = 'success'
              ORDER BY COALESCE(finished_at, created_at) DESC
              LIMIT 1
            ) b ON TRUE
            LEFT JOIN LATERAL (
              SELECT backup_label, status, rto_seconds, smoke_passed, smoke_total, finished_at, created_at
              FROM restore_run
              WHERE host_name = h.host_name AND LOWER(COALESCE(status, '')) = 'success'
              ORDER BY COALESCE(finished_at, created_at) DESC
              LIMIT 1
            ) r ON TRUE
            ORDER BY h.host_name
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("host", rs.getString("host_name"));
                row.put("latest_compliance_scan_id", asString(rs.getObject("compliance_scan_id")));
                row.put("latest_compliance_score", rs.getBigDecimal("compliance_score"));
                row.put("latest_compliance_status", rs.getString("compliance_status"));
                row.put("latest_compliance_at", asString(rs.getObject("compliance_at")));
                row.put("latest_backup_label", rs.getString("backup_label"));
                row.put("latest_backup_status", rs.getString("backup_status"));
                row.put("latest_backup_size_bytes", rs.getObject("backup_size_bytes"));
                row.put("latest_backup_at", asString(rs.getObject("backup_at")));
                row.put("latest_restore_backup_label", rs.getString("restore_backup_label"));
                row.put("latest_restore_status", rs.getString("restore_status"));
                row.put("latest_restore_rto_seconds", rs.getObject("rto_seconds"));
                row.put("latest_restore_smoke_passed", rs.getObject("smoke_passed"));
                row.put("latest_restore_smoke_total", rs.getObject("smoke_total"));
                row.put("latest_restore_at", asString(rs.getObject("restore_at")));
                return row;
            }
        );
    }

    public List<Map<String, Object>> listBackups() {
        return jdbcTemplate.query(
            """
            SELECT
              id,
              host_name,
              backup_type,
              backup_label,
              status,
              size_bytes,
              duration_seconds,
              started_at,
              finished_at,
              repo_path,
              details_path,
              created_at
            FROM backup_run
            ORDER BY COALESCE(finished_at, created_at) DESC
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", asString(rs.getObject("id")));
                row.put("host", rs.getString("host_name"));
                row.put("backup_type", rs.getString("backup_type"));
                row.put("backup_label", rs.getString("backup_label"));
                row.put("status", rs.getString("status"));
                row.put("size_bytes", rs.getObject("size_bytes"));
                row.put("duration_seconds", rs.getObject("duration_seconds"));
                row.put("started_at", asString(rs.getObject("started_at")));
                row.put("finished_at", asString(rs.getObject("finished_at")));
                row.put("repo_path", rs.getString("repo_path"));
                row.put("details_path", rs.getString("details_path"));
                row.put("created_at", asString(rs.getObject("created_at")));
                return row;
            }
        );
    }

    public List<Map<String, Object>> listRestores() {
        return jdbcTemplate.query(
            """
            SELECT
              id,
              host_name,
              backup_label,
              status,
              rows_expected,
              rows_restored,
              rto_seconds,
              smoke_passed,
              smoke_total,
              started_at,
              finished_at,
              details_path,
              created_at
            FROM restore_run
            ORDER BY COALESCE(finished_at, created_at) DESC
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", asString(rs.getObject("id")));
                row.put("host", rs.getString("host_name"));
                row.put("backup_label", rs.getString("backup_label"));
                row.put("status", rs.getString("status"));
                row.put("rows_expected", rs.getObject("rows_expected"));
                row.put("rows_restored", rs.getObject("rows_restored"));
                row.put("rto_seconds", rs.getObject("rto_seconds"));
                row.put("smoke_passed", rs.getObject("smoke_passed"));
                row.put("smoke_total", rs.getObject("smoke_total"));
                row.put("started_at", asString(rs.getObject("started_at")));
                row.put("finished_at", asString(rs.getObject("finished_at")));
                row.put("details_path", rs.getString("details_path"));
                row.put("created_at", asString(rs.getObject("created_at")));
                return row;
            }
        );
    }

    public int countHosts() {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM (
              SELECT host_name FROM compliance_scan
              UNION
              SELECT host_name FROM backup_run
              UNION
              SELECT host_name FROM restore_run
            ) hosts
            """,
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public int countComplianceScans() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM compliance_scan", Integer.class);
        return count == null ? 0 : count;
    }

    public int countBackups() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM backup_run", Integer.class);
        return count == null ? 0 : count;
    }

    public int countRestores() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM restore_run", Integer.class);
        return count == null ? 0 : count;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
