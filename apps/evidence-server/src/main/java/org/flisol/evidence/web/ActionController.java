package org.flisol.evidence.web;

import java.util.UUID;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * Acciones implementadas:
 * - POST /api/actions/run-scan
 * - POST /api/actions/run-backup
 * - POST /api/actions/run-restore
 * - GET  /api/actions/{id}/status
 */
@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final DemoSeedService demoSeedService;
    private final ActionJobService actionJobService;

    public ActionController(DemoSeedService demoSeedService, ActionJobService actionJobService) {
        this.demoSeedService = demoSeedService;
        this.actionJobService = actionJobService;
    }

    @PostMapping("/reload-dataset")
    public ResponseEntity<Map<String, Object>> reloadDataset() {
        Map<String, Object> result = demoSeedService.reloadDemoDataset();
        boolean ok = "COMPLETED".equals(result.get("status"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.internalServerError().body(result);
    }

    @PostMapping("/run-scan")
    public ResponseEntity<Map<String, Object>> runScan() {
        return ResponseEntity.ok(actionJobService.runScan());
    }

    @PostMapping("/run-backup")
    public ResponseEntity<Map<String, Object>> runBackup() {
        return ResponseEntity.ok(actionJobService.runBackup());
    }

    @PostMapping("/run-restore")
    public ResponseEntity<Map<String, Object>> runRestore() {
        return ResponseEntity.ok(actionJobService.runRestore());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getActionStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(actionJobService.getJobStatus(id));
    }
}
