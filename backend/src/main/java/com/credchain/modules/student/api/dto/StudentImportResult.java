package com.credchain.modules.student.api.dto;

import java.util.List;

/**
 * Report returned after a CSV import.
 * Row numbers match the spreadsheet: the header is row 1, the first student is row 2.
 */
public record StudentImportResult(
        int totalRows,
        int valid,
        int imported,
        int failed,
        boolean dryRun,
        List<RowError> errors
) {

    public record RowError(int row, String enrollmentNo, List<String> messages) {
    }
}