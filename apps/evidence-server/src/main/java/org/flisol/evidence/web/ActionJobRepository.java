package org.flisol.evidence.web;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActionJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public ActionJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(ActionJob job) {
        jdbcTemplate.update(
            """
            INSERT INTO action_job (
              id, action_name, host_name, status,
              command_line, output_log, error_message,
              started_at, finished_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            job.getId(),
            job.getActionName(),
            job.getHostName(),
            job.getStatus(),
            job.getCommandLine(),
            job.getOutputLog(),
            job.getErrorMessage(),
            toTimestamp(job.getStartedAt()),
            toTimestamp(job.getFinishedAt()),
            job.getCreatedAt() != null ? Timestamp.from(job.getCreatedAt()) : Timestamp.from(Instant.now())
        );
    }

    public void update(ActionJob job) {
        jdbcTemplate.update(
            """
            UPDATE action_job
               SET status = ?,
                   command_line = ?,
                   output_log = ?,
                   error_message = ?,
                   started_at = ?,
                   finished_at = ?
             WHERE id = ?
            """,
            job.getStatus(),
            job.getCommandLine(),
            job.getOutputLog(),
            job.getErrorMessage(),
            toTimestamp(job.getStartedAt()),
            toTimestamp(job.getFinishedAt()),
            job.getId()
        );
    }

    public Map<String, Object> findById(UUID id) {
        List<Map<String, Object>> list = jdbcTemplate.query(
            """
            SELECT id, action_name, host_name, status, command_line, output_log, error_message,
                   started_at, finished_at, created_at
              FROM action_job
             WHERE id = ?
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", asString(rs.getObject("id")));
                row.put("action_name", rs.getString("action_name"));
                row.put("host_name", rs.getString("host_name"));
                row.put("status", rs.getString("status"));
                row.put("command_line", rs.getString("command_line"));
                row.put("output_log", rs.getString("output_log"));
                row.put("error_message", rs.getString("error_message"));
                row.put("started_at", asString(rs.getObject("started_at")));
                row.put("finished_at", asString(rs.getObject("finished_at")));
                row.put("created_at", asString(rs.getObject("created_at")));
                return row;
            },
            id
        );
        return list.isEmpty() ? null : list.getFirst();
    }

    public boolean hasActiveJobs() {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM action_job
            WHERE status IN ('PENDING', 'RUNNING')
            """,
            Integer.class
        );
        return count != null && count > 0;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
