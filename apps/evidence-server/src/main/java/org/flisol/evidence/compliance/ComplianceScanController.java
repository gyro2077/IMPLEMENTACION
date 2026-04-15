package org.flisol.evidence.compliance;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceScanController {

    private final ComplianceScanService complianceScanService;

    public ComplianceScanController(ComplianceScanService complianceScanService) {
        this.complianceScanService = complianceScanService;
    }

    // Importa el resumen parseado del ARF para persistencia minima.
    @PostMapping("/scans/import")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> importScan(@RequestBody Map<String, Object> payload) {
        return complianceScanService.importScan(payload);
    }

    // Lista scans guardados y top 10 de hallazgos fallidos por scan.
    @GetMapping("/scans")
    public List<Map<String, Object>> listScans() {
        return complianceScanService.listScans();
    }
}
