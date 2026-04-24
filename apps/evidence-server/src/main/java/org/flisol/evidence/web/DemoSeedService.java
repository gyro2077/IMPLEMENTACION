package org.flisol.evidence.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flisol.evidence.compliance.ComplianceScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Servicio para recargar el dataset de demostración de forma segura.
 * Replica la lógica de automation/scripts/seed_demo_data.sh sin ejecutar shell.
 *
 * Operaciones:
 * 1. Truncar tablas de metadata (JdbcTemplate directo)
 * 2. Importar scan de compliance demo (ComplianceScanService.importScan)
 * 3. Generar tráfico mínimo a target-app (RestTemplate)
 */
@Service
public class DemoSeedService {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ComplianceScanService complianceScanService;

    public DemoSeedService(JdbcTemplate jdbcTemplate, ComplianceScanService complianceScanService) {
        this.jdbcTemplate = jdbcTemplate;
        this.complianceScanService = complianceScanService;
    }

    public Map<String, Object> reloadDemoDataset() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("started_at", Instant.now().toString());

        try {
            // 1. Limpiar tablas de metadata
            truncateMetadata();
            result.put("truncate", "ok");

            // 2. Importar compliance demo
            Map<String, Object> importResult = importDemoCompliance();
            result.put("compliance_import", importResult);

            // 3. Tráfico mínimo para observabilidad
            int pings = generateObservabilityTraffic();
            result.put("traffic_pings", pings);

            result.put("status", "COMPLETED");
            result.put("message", "Dataset demo recargado exitosamente.");
        } catch (Exception ex) {
            log.error("Error al recargar dataset demo", ex);
            result.put("status", "FAILED");
            result.put("message", "Error: " + ex.getMessage());
        }

        result.put("finished_at", Instant.now().toString());
        return result;
    }

    private void truncateMetadata() {
        log.info("[seed] Truncando tablas de metadata");
        jdbcTemplate.execute("""
            DO $$
            BEGIN
              IF to_regclass('public.compliance_rule_result') IS NOT NULL THEN
                TRUNCATE TABLE compliance_rule_result CASCADE;
              END IF;
              IF to_regclass('public.compliance_scan') IS NOT NULL THEN
                TRUNCATE TABLE compliance_scan CASCADE;
              END IF;
              IF to_regclass('public.backup_run') IS NOT NULL THEN
                TRUNCATE TABLE backup_run CASCADE;
              END IF;
              IF to_regclass('public.restore_run') IS NOT NULL THEN
                TRUNCATE TABLE restore_run CASCADE;
              END IF;
              IF to_regclass('public.report_job') IS NOT NULL THEN
                TRUNCATE TABLE report_job CASCADE;
              END IF;
            END $$
            """);
    }

    private Map<String, Object> importDemoCompliance() {
        log.info("[seed] Importando compliance demo");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime started = now.minusMinutes(2);
        String isoNow = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String isoStarted = started.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("host", "target-cachyos");
        payload.put("profile_id", "xccdf_org.ssgproject.content_profile_cis_level1_server");
        payload.put("score", 82.5);
        payload.put("status", "completed");
        payload.put("arf_path", "fixtures/demo/compliance/scan-results.arf.xml");
        payload.put("html_report_path", "fixtures/demo/compliance/scan-report.html");
        payload.put("started_at", isoStarted);
        payload.put("finished_at", isoNow);

        payload.put("failed_rules", List.of(
            Map.of("rule_id", "xccdf_org.ssgproject.content_rule_no_empty_passwords",
                   "severity", "high", "result", "fail",
                   "title", "No permitir cuentas con password vacia"),
            Map.of("rule_id", "xccdf_org.ssgproject.content_rule_sshd_disable_root_login",
                   "severity", "medium", "result", "fail",
                   "title", "Deshabilitar login root por SSH"),
            Map.of("rule_id", "xccdf_org.ssgproject.content_rule_firewalld_enabled",
                   "severity", "medium", "result", "fail",
                   "title", "Mantener firewall habilitado")
        ));

        return complianceScanService.importScan(payload);
    }

    private int generateObservabilityTraffic() {
        log.info("[seed] Generando tráfico mínimo para observabilidad");
        RestTemplate rest = new RestTemplate();
        int successful = 0;
        for (int i = 0; i < 5; i++) {
            try {
                rest.getForObject("http://target-app:8080/api/ping", String.class);
                successful++;
            } catch (Exception ex) {
                log.debug("[seed] Ping {} falló: {}", i, ex.getMessage());
            }
        }
        return successful;
    }
}
