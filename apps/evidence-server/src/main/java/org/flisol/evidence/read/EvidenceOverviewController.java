package org.flisol.evidence.read;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceOverviewController {

    private final EvidenceReadService evidenceReadService;

    public EvidenceOverviewController(EvidenceReadService evidenceReadService) {
        this.evidenceReadService = evidenceReadService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return evidenceReadService.overview();
    }
}
