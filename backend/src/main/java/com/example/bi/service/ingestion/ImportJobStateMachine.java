package com.example.bi.service.ingestion;

import com.example.bi.domain.ImportJobStatus;

import java.util.Map;
import java.util.Set;

public final class ImportJobStateMachine {

    private static final Map<ImportJobStatus, Set<ImportJobStatus>> ALLOWED_TRANSITIONS = Map.of(
            ImportJobStatus.PARSING, Set.of(ImportJobStatus.VALIDATED, ImportJobStatus.FAILED),
            ImportJobStatus.VALIDATED, Set.of(ImportJobStatus.COMPLETED, ImportJobStatus.CANCELLED, ImportJobStatus.EXPIRED),
            ImportJobStatus.COMPLETED, Set.of(),
            ImportJobStatus.FAILED, Set.of(),
            ImportJobStatus.CANCELLED, Set.of(),
            ImportJobStatus.EXPIRED, Set.of()
    );

    private ImportJobStateMachine() {}

    public static boolean canTransition(ImportJobStatus current, ImportJobStatus target) {
        if (current == null || target == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    public static void validateTransition(ImportJobStatus current, ImportJobStatus target) {
        if (!canTransition(current, target)) {
            throw new IllegalStateException(
                    String.format("Illegal import job state transition from %s to %s", current, target)
            );
        }
    }
}
