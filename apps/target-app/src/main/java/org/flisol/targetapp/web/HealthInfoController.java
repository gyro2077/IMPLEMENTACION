package org.flisol.targetapp.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthInfoController {

    // Endpoint simple para validar que la app responde fuera de actuator.
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "service", "target-app",
            "status", "ok",
            "timestamp", Instant.now().toString()
        );
    }
}
