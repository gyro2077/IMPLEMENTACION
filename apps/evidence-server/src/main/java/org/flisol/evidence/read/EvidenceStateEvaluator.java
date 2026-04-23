package org.flisol.evidence.read;

import java.util.LinkedHashMap;
import java.util.Map;

public class EvidenceStateEvaluator {

    public static Map<String, String> evaluate(Map<String, Object> hostData) {
        double score = parseDouble(hostData.get("latest_compliance_score"));
        boolean hasCompliance = hostData.get("latest_compliance_scan_id") != null;
        boolean hasBackup = hostData.get("latest_backup_label") != null;
        boolean hasRestore = hostData.get("latest_restore_backup_label") != null;

        String backupStatus = asText(hostData.get("latest_backup_status")).toLowerCase();
        String restoreStatus = asText(hostData.get("latest_restore_status")).toLowerCase();
        int smokePassed = parseInt(hostData.get("latest_restore_smoke_passed"));
        int smokeTotal = parseInt(hostData.get("latest_restore_smoke_total"));

        boolean hasCriticalProblem = (hasCompliance && score > 0 && score < 70)
                || isNegativeStatus(backupStatus)
                || isNegativeStatus(restoreStatus);

        boolean hasObservations = (smokeTotal > 0 && smokePassed < smokeTotal);

        String statusKey;
        String statusLabel;
        String statusMessage;

        if (hasCriticalProblem) {
            statusKey = "problem";
            statusLabel = "Problema";
            statusMessage = "Se detecta riesgo operativo crítico o evidencia fallida. Requiere atención inmediata.";
        } else if (hasCompliance && hasBackup && hasRestore) {
            if (hasObservations) {
                statusKey = "partial";
                statusLabel = "Validado con observaciones";
                statusMessage = "Host validado, pero faltan pasos menores (ej. un smoke test skipeado).";
            } else {
                statusKey = "ready";
                statusLabel = "Listo";
                statusMessage = "Host con evidencia completa y validada exitosamente para auditoría.";
            }
        } else if (hasCompliance || hasBackup || hasRestore) {
            statusKey = "partial";
            statusLabel = "Parcial";
            statusMessage = "Hay evidencia parcial, pero faltan fases para cerrar el ciclo completo.";
        } else {
            statusKey = "empty";
            statusLabel = "Sin evidencia";
            statusMessage = "Aún no se han cargado datos de cumplimiento, backup o restore.";
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("status_key", statusKey);
        result.put("status_label", statusLabel);
        result.put("status_message", statusMessage);
        return result;
    }

    private static boolean isNegativeStatus(String status) {
        return status.contains("fail") || status.contains("error") || status.contains("warning");
    }

    private static int parseInt(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double parseDouble(Object value) {
        if (value == null) return 0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String asText(Object value) {
        return value == null ? "-" : value.toString();
    }
}
