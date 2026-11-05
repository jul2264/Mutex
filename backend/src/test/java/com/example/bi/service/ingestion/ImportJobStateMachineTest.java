package com.example.bi.service.ingestion;

import com.example.bi.domain.ImportJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ImportJobStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "PARSING, VALIDATED",
            "PARSING, FAILED",
            "VALIDATED, COMPLETED",
            "VALIDATED, CANCELLED",
            "VALIDATED, EXPIRED"
    })
    void allowedTransitions_succeed(ImportJobStatus from, ImportJobStatus to) {
        assertTrue(ImportJobStateMachine.canTransition(from, to));
        assertDoesNotThrow(() -> ImportJobStateMachine.validateTransition(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "PARSING, COMPLETED",
            "PARSING, CANCELLED",
            "PARSING, EXPIRED",
            "VALIDATED, PARSING",
            "VALIDATED, FAILED",
            "COMPLETED, VALIDATED",
            "COMPLETED, PARSING",
            "FAILED, PARSING",
            "FAILED, VALIDATED",
            "CANCELLED, VALIDATED",
            "EXPIRED, VALIDATED"
    })
    void illegalTransitions_fail(ImportJobStatus from, ImportJobStatus to) {
        assertFalse(ImportJobStateMachine.canTransition(from, to));
        assertThrows(IllegalStateException.class, () -> ImportJobStateMachine.validateTransition(from, to));
    }

    @Test
    void nullTransitions_fail() {
        assertFalse(ImportJobStateMachine.canTransition(null, ImportJobStatus.VALIDATED));
        assertFalse(ImportJobStateMachine.canTransition(ImportJobStatus.PARSING, null));
    }
}
