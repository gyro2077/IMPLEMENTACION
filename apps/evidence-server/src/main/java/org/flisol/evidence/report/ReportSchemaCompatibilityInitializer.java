package org.flisol.evidence.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReportSchemaCompatibilityInitializer {

    private static final Logger log = LoggerFactory.getLogger(ReportSchemaCompatibilityInitializer.class);

    public ReportSchemaCompatibilityInitializer(JdbcTemplate jdbcTemplate) {
        ensureReportJobCompatibility(jdbcTemplate);
    }

    private void ensureReportJobCompatibility(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS report_job (
                  id UUID PRIMARY KEY,
                  host_name VARCHAR(120) NOT NULL,
                  status VARCHAR(30) NOT NULL,
                  pdf_path TEXT,
                  error_message TEXT,
                  started_at TIMESTAMPTZ,
                  finished_at TIMESTAMPTZ,
                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);

            jdbcTemplate.execute("ALTER TABLE report_job ADD COLUMN IF NOT EXISTS host_name VARCHAR(120)");
            jdbcTemplate.execute("ALTER TABLE report_job ADD COLUMN IF NOT EXISTS status VARCHAR(30)");
            jdbcTemplate.execute("ALTER TABLE report_job ADD COLUMN IF NOT EXISTS pdf_path TEXT");
            jdbcTemplate.execute("ALTER TABLE report_job ADD COLUMN IF NOT EXISTS error_message TEXT");
            jdbcTemplate.execute("ALTER TABLE report_job ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ");
            jdbcTemplate.execute("ALTER TABLE report_job ADD COLUMN IF NOT EXISTS finished_at TIMESTAMPTZ");
            jdbcTemplate.execute("ALTER TABLE report_job ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()");

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_report_job_created_at ON report_job(created_at DESC)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_report_job_host_name ON report_job(host_name)");
        } catch (Exception ex) {
            log.error("No se pudo aplicar compatibilidad de esquema para report_job", ex);
            throw ex;
        }
    }
}
