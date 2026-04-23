package org.flisol.evidence.read;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flisol.evidence.compliance.ComplianceScanService;
import org.springframework.stereotype.Service;

@Service
public class EvidenceReadService {

    private final EvidenceReadRepository repository;
    private final ComplianceScanService complianceScanService;

    public EvidenceReadService(EvidenceReadRepository repository, ComplianceScanService complianceScanService) {
        this.repository = repository;
        this.complianceScanService = complianceScanService;
    }

    public List<Map<String, Object>> listHosts() {
        List<Map<String, Object>> hosts = repository.listHostsWithLatestEvidence();
        for (Map<String, Object> host : hosts) {
            Map<String, String> evaluation = EvidenceStateEvaluator.evaluate(host);
            host.put("evidence_status", evaluation.get("status_key"));
            host.put("evidence_status_label", evaluation.get("status_label"));
            host.put("evidence_status_message", evaluation.get("status_message"));
        }
        return hosts;
    }

    public List<Map<String, Object>> listBackups() {
        return repository.listBackups();
    }

    public List<Map<String, Object>> listRestores() {
        return repository.listRestores();
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> hosts = listHosts();

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("hosts", repository.countHosts());
        totals.put("compliance_scans", repository.countComplianceScans());
        totals.put("backups", repository.countBackups());
        totals.put("restores", repository.countRestores());

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("generated_at", Instant.now().toString());
        overview.put("totals", totals);
        overview.put("hosts", hosts);
        return overview;
    }

    public List<Map<String, Object>> listScans() {
        return complianceScanService.listScans();
    }
}
