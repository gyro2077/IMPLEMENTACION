package org.flisol.evidence.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/evidence/{hostId}")
    public ResponseEntity<ReportJob> generateEvidenceReport(@PathVariable String hostId) {
        ReportJob job = reportService.generateReport(hostId);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReportJobStatus(@PathVariable UUID id) {
        Map<String, Object> status = reportService.getJobStatus(id);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadPdf(@PathVariable UUID id) {
        Map<String, Object> job = reportService.getJobStatus(id);
        String pdfPathStr = (String) job.get("pdf_path");
        String status = ((String) job.get("status")).toLowerCase();

        if (!"completed".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reporte aun no disponible. Estado actual: " + status);
        }

        if (pdfPathStr == null || pdfPathStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El job no tiene ruta de PDF asociada.");
        }

        Path file = Paths.get(pdfPathStr);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF no encontrado en disco: " + pdfPathStr);
        }

        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se puede leer el PDF generado.");
            }

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al abrir el PDF.", ex);
        }
    }
}
