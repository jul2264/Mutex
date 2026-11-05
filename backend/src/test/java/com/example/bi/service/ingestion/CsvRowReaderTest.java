package com.example.bi.service.ingestion;

import com.example.bi.exception.FileValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CsvRowReaderTest {

    @Test
    void standardCsv_readsHeadersAndRowsCorrectly(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("sample.csv");
        Files.writeString(csv, """
                order_id,order_date,product_name,category,region,quantity,unit_price,unit_cost
                ORD-1,2026-01-01,Widget,Tools,North,5,100.00,50.00
                """);

        try (CsvRowReader reader = new CsvRowReader(csv.toFile(), 1000, 50)) {
            assertEquals(8, reader.getHeaderNames().size());
            assertTrue(reader.getExtraColumns().isEmpty());

            assertTrue(reader.hasNext());
            RawRow row = reader.nextRow();
            assertEquals(2, row.sourceRowNumber());
            assertFalse(row.isBlank());
            assertEquals("ORD-1", row.cells().get("order_id"));
            assertEquals("Widget", row.cells().get("product_name"));
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void headerWithBomAndSpaces_normalizedCorrectly(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("bom.csv");
        // Prepend BOM \uFEFF to first column name
        Files.writeString(csv, """
                \uFEFFOrder Id,Order Date,Product Name,Category,Region,Quantity,Unit Price,Unit Cost,Extra Note
                ORD-1,2026-01-01,Widget,Tools,North,5,100.00,50.00,note
                """);

        try (CsvRowReader reader = new CsvRowReader(csv.toFile(), 1000, 50)) {
            assertTrue(reader.getHeaderNames().contains("order_id"));
            assertTrue(reader.getHeaderNames().contains("product_name"));
            assertEquals(1, reader.getExtraColumns().size());
            assertEquals("extra_note", reader.getExtraColumns().get(0));
        }
    }

    @Test
    void missingRequiredColumn_throwsMissingColumnException(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("missing.csv");
        Files.writeString(csv, "order_id,order_date,category,region,quantity,unit_price,unit_cost\nORD-1,2026-01-01,Tools,North,5,100,50\n");

        FileValidationException ex = assertThrows(FileValidationException.class, () -> new CsvRowReader(csv.toFile(), 1000, 50));
        assertEquals("MISSING_COLUMN", ex.getCode());
        assertTrue(ex.getMessage().contains("product_name"));
    }

    @Test
    void duplicateHeader_throwsDuplicateHeaderException(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("dup.csv");
        Files.writeString(csv, "order_id,order_date,product_name,category,region,quantity,unit_price,unit_cost,order_id\n");

        FileValidationException ex = assertThrows(FileValidationException.class, () -> new CsvRowReader(csv.toFile(), 1000, 50));
        assertEquals("DUPLICATE_HEADER", ex.getCode());
        assertTrue(ex.getMessage().contains("order_id"));
    }

    @Test
    void semicolonDelimiter_sniffedAndParsed(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("semi.csv");
        Files.writeString(csv, "order_id;order_date;product_name;category;region;quantity;unit_price;unit_cost\nORD-1;2026-01-01;Widget;Tools;North;5;100.00;50.00\n");

        try (CsvRowReader reader = new CsvRowReader(csv.toFile(), 1000, 50)) {
            assertTrue(reader.hasNext());
            RawRow row = reader.nextRow();
            assertEquals("ORD-1", row.cells().get("order_id"));
            assertEquals("Widget", row.cells().get("product_name"));
        }
    }

    @Test
    void blankLine_emittedAsBlank(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("blank.csv");
        Files.writeString(csv, """
                order_id,order_date,product_name,category,region,quantity,unit_price,unit_cost
                ORD-1,2026-01-01,Widget,Tools,North,5,100.00,50.00
                
                ORD-2,2026-01-02,Gadget,Tools,North,2,50.00,20.00
                """);

        try (CsvRowReader reader = new CsvRowReader(csv.toFile(), 1000, 50)) {
            RawRow r1 = reader.nextRow();
            assertEquals(2, r1.sourceRowNumber());
            assertFalse(r1.isBlank());

            RawRow rBlank = reader.nextRow();
            assertEquals(3, rBlank.sourceRowNumber());
            assertTrue(rBlank.isBlank());

            RawRow r2 = reader.nextRow();
            assertEquals(4, r2.sourceRowNumber());
            assertFalse(r2.isBlank());
        }
    }
}
