package org.flisol.evidence.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de acciones seguras ejecutables desde el Dashboard.
 *
 * Patrón de seguridad:
 * - Solo acciones pre-aprobadas (whitelist, no input del usuario)
 * - Cada acción es un método explícito, no un dispatch genérico
 * - Nunca se expone ejecución de shell
 * - Resultado siempre persistido o retornado con estado claro
 *
 * Acciones futuras (ETAPA 2+):
 * - POST /api/actions/execute-backup  → requiere ActionJobService + pgBackRest agent
 * - POST /api/actions/execute-restore → requiere container orchestration
 * - POST /api/actions/execute-scan    → requiere OpenSCAP agent
 */
@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final DemoSeedService demoSeedService;

    public ActionController(DemoSeedService demoSeedService) {
        this.demoSeedService = demoSeedService;
    }

    @PostMapping("/reload-dataset")
    public ResponseEntity<Map<String, Object>> reloadDataset() {
        Map<String, Object> result = demoSeedService.reloadDemoDataset();
        boolean ok = "COMPLETED".equals(result.get("status"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.internalServerError().body(result);
    }
}
