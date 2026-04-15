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
