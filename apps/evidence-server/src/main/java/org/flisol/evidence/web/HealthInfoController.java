package org.flisol.evidence.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthInfoController {

    // Endpoint simple para verificar la API central de evidencia.
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "service", "evidence-server",
            "status", "ok",
            "timestamp", Instant.now().toString()
        );
    }
}
