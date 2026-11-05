package com.example.bi.service.ingestion;

import com.example.bi.exception.FileValidationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class XlsxRowReaderTest {

    @Test
    void standardXlsx_readsHeadersAndRowsWithFormatting(@TempDir Path tempDir) throws IOException {
        Path xlsxPath = tempDir.resolve("test.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sales");

            // Header
            Row header = sheet.createRow(0);
            String[] cols = {"order_id", "order_date", "product_name", "category", "region", "quantity", "unit_price", "unit_cost"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            // Data row
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue(123.0); // Numeric order_id

            // Date cell
            Cell dateCell = data.createCell(1);
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));
            dateCell.setCellValue(LocalDate.of(2026, 1, 15));
            dateCell.setCellStyle(dateStyle);

            data.createCell(2).setCellValue("Ergonomic Keyboard");
            data.createCell(3).setCellValue("Electronics");
            data.createCell(4).setCellValue("North");
            data.createCell(5).setCellValue(5.0); // Quantity as double
            data.createCell(6).setCellValue(1500.50);
            data.createCell(7).setCellValue(1000.00);

            try (FileOutputStream fos = new FileOutputStream(xlsxPath.toFile())) {
                workbook.write(fos);
            }
        }

        try (XlsxRowReader reader = new XlsxRowReader(xlsxPath.toFile(), 1000, 50)) {
            assertEquals(8, reader.getHeaderNames().size());
            assertTrue(reader.hasNext());

            RawRow row = reader.nextRow();
            assertEquals(2, row.sourceRowNumber());
            assertFalse(row.isBlank());
            assertEquals("123", row.cells().get("order_id")); // Clean integer
            assertEquals("2026-01-15", row.cells().get("order_date")); // Formatted date
            assertEquals("Ergonomic Keyboard", row.cells().get("product_name"));
            assertEquals("5", row.cells().get("quantity"));

            assertFalse(reader.hasNext());
        }
    }

    @Test
    void missingHeader_throwsMissingColumnException(@TempDir Path tempDir) throws IOException {
        Path xlsxPath = tempDir.resolve("missing.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sales");
            Row header = sheet.createRow(0);
            // Missing product_name
            String[] cols = {"order_id", "order_date", "category", "region", "quantity", "unit_price", "unit_cost"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            try (FileOutputStream fos = new FileOutputStream(xlsxPath.toFile())) {
                workbook.write(fos);
            }
        }

        FileValidationException ex = assertThrows(FileValidationException.class, () -> new XlsxRowReader(xlsxPath.toFile(), 1000, 50));
        assertEquals("MISSING_COLUMN", ex.getCode());
        assertTrue(ex.getMessage().contains("product_name"));
    }

    @Test
    void duplicateHeader_throwsDuplicateHeaderException(@TempDir Path tempDir) throws IOException {
        Path xlsxPath = tempDir.resolve("duplicate.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sales");
            Row header = sheet.createRow(0);
            String[] cols = {"order_id", "order_date", "product_name", "category", "region", "quantity", "unit_price", "unit_cost", "order_id"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            try (FileOutputStream fos = new FileOutputStream(xlsxPath.toFile())) {
                workbook.write(fos);
            }
        }

        FileValidationException ex = assertThrows(FileValidationException.class, () -> new XlsxRowReader(xlsxPath.toFile(), 1000, 50));
        assertEquals("DUPLICATE_HEADER", ex.getCode());
        assertTrue(ex.getMessage().contains("order_id"));
    }
}

