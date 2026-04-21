package org.flisol.evidence.read;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hosts")
public class HostController {

    private final EvidenceReadService evidenceReadService;

    public HostController(EvidenceReadService evidenceReadService) {
        this.evidenceReadService = evidenceReadService;
    }

    @GetMapping
    public List<Map<String, Object>> listHosts() {
        return evidenceReadService.listHosts();
    }
}
