-- Fase 3B: persistencia minima de escaneos de cumplimiento.
CREATE TABLE IF NOT EXISTS compliance_scan (
  id UUID PRIMARY KEY,
  host_name VARCHAR(120) NOT NULL,
  profile_id VARCHAR(220) NOT NULL,
  score NUMERIC(7,2),
  status VARCHAR(30) NOT NULL,
  arf_path TEXT NOT NULL,
  html_report_path TEXT NOT NULL,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS compliance_rule_result (
  id UUID PRIMARY KEY,
  scan_id UUID NOT NULL REFERENCES compliance_scan(id) ON DELETE CASCADE,
  rule_id VARCHAR(250) NOT NULL,
  severity VARCHAR(30),
  result VARCHAR(30),
  title TEXT
);

CREATE INDEX IF NOT EXISTS idx_compliance_scan_created_at
  ON compliance_scan(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compliance_rule_scan_id
  ON compliance_rule_result(scan_id);

-- Fase 4: persistencia minima de backup y restore PostgreSQL.
CREATE TABLE IF NOT EXISTS backup_run (
  id UUID PRIMARY KEY,
  host_name VARCHAR(120) NOT NULL,
  backup_type VARCHAR(30) NOT NULL,
  backup_label VARCHAR(120),
  status VARCHAR(30) NOT NULL,
  size_bytes BIGINT,
  duration_seconds INTEGER,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  repo_path TEXT,
  details_path TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS restore_run (
  id UUID PRIMARY KEY,
  host_name VARCHAR(120) NOT NULL,
  backup_label VARCHAR(120),
  status VARCHAR(30) NOT NULL,
  rows_expected INTEGER,
  rows_restored INTEGER,
  rto_seconds INTEGER,
  smoke_passed INTEGER,
  smoke_total INTEGER,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  details_path TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_backup_run_created_at
  ON backup_run(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_restore_run_created_at
  ON restore_run(created_at DESC);

-- Fase 5B: persistencia de jobs de reporte PDF.
CREATE TABLE IF NOT EXISTS report_job (
  id UUID PRIMARY KEY,
  host_name VARCHAR(120) NOT NULL,
  status VARCHAR(30) NOT NULL,
  pdf_path TEXT,
  error_message TEXT,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE report_job ADD COLUMN IF NOT EXISTS host_name VARCHAR(120);
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS pdf_path TEXT;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS finished_at TIMESTAMPTZ;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_report_job_created_at
  ON report_job(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_report_job_host_name
  ON report_job(host_name);

-- Fase 6: jobs de acciones operativas seguras del dashboard.
CREATE TABLE IF NOT EXISTS action_job (
  id UUID PRIMARY KEY,
  action_name VARCHAR(60) NOT NULL,
  host_name VARCHAR(120) NOT NULL,
  status VARCHAR(30) NOT NULL,
  command_line TEXT,
  output_log TEXT,
  error_message TEXT,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_action_job_created_at
  ON action_job(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_action_job_action_name
  ON action_job(action_name);
