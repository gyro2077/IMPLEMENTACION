package org.flisol.evidence.report;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(ReportJob job) {
        jdbcTemplate.update(
            """
            INSERT INTO report_job (id, host_name, status, pdf_path, error_message, started_at, finished_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            job.getId(),
            job.getHostName(),
            job.getStatus(),
            job.getPdfPath(),
            job.getErrorMessage(),
            job.getStartedAt() != null ? Timestamp.from(job.getStartedAt()) : null,
            job.getFinishedAt() != null ? Timestamp.from(job.getFinishedAt()) : null,
            job.getCreatedAt() != null ? Timestamp.from(job.getCreatedAt()) : Timestamp.from(Instant.now())
        );
    }

    public void update(ReportJob job) {
        jdbcTemplate.update(
            """
            UPDATE report_job
            SET status = ?, pdf_path = ?, error_message = ?, started_at = ?, finished_at = ?
            WHERE id = ?
            """,
            job.getStatus(),
            job.getPdfPath(),
            job.getErrorMessage(),
            job.getStartedAt() != null ? Timestamp.from(job.getStartedAt()) : null,
            job.getFinishedAt() != null ? Timestamp.from(job.getFinishedAt()) : null,
            job.getId()
        );
    }

    public Map<String, Object> findById(UUID id) {
        List<Map<String, Object>> list = jdbcTemplate.query(
            "SELECT * FROM report_job WHERE id = ?",
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getObject("id").toString());
                row.put("host_name", rs.getString("host_name"));
                row.put("status", rs.getString("status"));
                row.put("pdf_path", rs.getString("pdf_path"));
                row.put("error_message", rs.getString("error_message"));
                row.put("started_at", asString(rs.getObject("started_at")));
                row.put("finished_at", asString(rs.getObject("finished_at")));
                row.put("created_at", asString(rs.getObject("created_at")));
                return row;
            },
            id
        );
        return list.isEmpty() ? null : list.get(0);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
