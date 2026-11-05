package com.example.bi.service.ingestion;

import com.example.bi.exception.FileValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvRowReader implements Closeable {

    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "order_id", "order_date", "product_name", "category", "region", "quantity", "unit_price", "unit_cost"
    );

    private final BufferedReader reader;
    private final CSVParser parser;
    private final List<String> headerNames;
    private final List<String> extraColumns;
    private final Iterator<CSVRecord> recordIterator;
    private final int maxRows;
    private final int maxColumns;
    private int currentRowNumber = 1; // Header is row 1
    private int dataRowsCount = 0;

    public CsvRowReader(File file, int maxRows, int maxColumns) throws IOException {
        this.maxRows = maxRows;
        this.maxColumns = maxColumns;

        char delimiter = sniffDelimiter(file);

        BufferedReader br = null;
        CSVParser p = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));

            CSVFormat format = CSVFormat.Builder.create()
                    .setDelimiter(delimiter)
                    .setIgnoreSurroundingSpaces(true)
                    .setIgnoreEmptyLines(false) // Preserve empty lines so row numbers match spreadsheets
                    .setQuote('"')
                    .build();

            p = format.parse(br);
            Iterator<CSVRecord> it = p.iterator();

            if (!it.hasNext()) {
                throw new FileValidationException("FILE_EMPTY", "File has no header row");
            }

            CSVRecord headerRecord = it.next();
            if (headerRecord.size() > maxColumns) {
                throw new FileValidationException("TOO_MANY_COLUMNS", String.format("File has %d columns, exceeding maximum of %d", headerRecord.size(), maxColumns));
            }

            List<String> headers = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < headerRecord.size(); i++) {
                String raw = headerRecord.get(i);
                String norm = normalizeHeader(raw);
                if (norm.isEmpty()) {
                    norm = "column_" + (i + 1);
                }
                if (!seen.add(norm)) {
                    throw new FileValidationException("DUPLICATE_HEADER", "Duplicate column name: " + norm);
                }
                headers.add(norm);
            }

            // Validate required columns
            List<String> missing = REQUIRED_COLUMNS.stream()
                    .filter(col -> !seen.contains(col))
                    .sorted()
                    .toList();

            if (!missing.isEmpty()) {
                throw new FileValidationException("MISSING_COLUMN", "Missing required column(s): " + String.join(", ", missing));
            }

            this.reader = br;
            this.parser = p;
            this.recordIterator = it;
            this.headerNames = Collections.unmodifiableList(headers);
            this.extraColumns = headers.stream()
                    .filter(h -> !REQUIRED_COLUMNS.contains(h))
                    .toList();

        } catch (Throwable t) {
            if (p != null) {
                try { p.close(); } catch (Exception ignored) {}
            }
            if (br != null) {
                try { br.close(); } catch (Exception ignored) {}
            }
            if (t instanceof IOException e) {
                throw e;
            }
            if (t instanceof RuntimeException e) {
                throw e;
            }
            throw new RuntimeException(t);
        }
    }

    public List<String> getHeaderNames() {
        return headerNames;
    }

    public List<String> getExtraColumns() {
        return extraColumns;
    }

    public boolean hasNext() {
        return recordIterator.hasNext();
    }

    public RawRow nextRow() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        currentRowNumber++;
        CSVRecord record = recordIterator.next();

        if (record.size() > maxColumns) {
            throw new FileValidationException("TOO_MANY_COLUMNS", String.format("Row %d has %d columns, exceeding limit of %d", currentRowNumber, record.size(), maxColumns));
        }

        boolean isBlank = true;
        Map<String, String> cells = new HashMap<>();

        for (int i = 0; i < headerNames.size(); i++) {
            String val = (i < record.size()) ? record.get(i) : null;
            if (val != null && !val.strip().isEmpty()) {
                isBlank = false;
            }
            cells.put(headerNames.get(i), val);
        }

        if (!isBlank) {
            dataRowsCount++;
            if (dataRowsCount > maxRows) {
                throw new FileValidationException("TOO_MANY_ROWS", String.format("File exceeds maximum allowed limit of %d rows", maxRows));
            }
        }

        return new RawRow(currentRowNumber, cells, isBlank);
    }

    public static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        String clean = raw.replace("\uFEFF", "").strip();
        clean = clean.toLowerCase(Locale.ROOT).replace(' ', '_');
        return clean;
    }

    private static char sniffDelimiter(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String firstLine = br.readLine();
            if (firstLine == null) {
                return ',';
            }
            long commas = firstLine.chars().filter(c -> c == ',').count();
            long semicolons = firstLine.chars().filter(c -> c == ';').count();
            long tabs = firstLine.chars().filter(c -> c == '\t').count();

            if (semicolons > commas && semicolons >= tabs) {
                return ';';
            }
            if (tabs > commas && tabs > semicolons) {
                return '\t';
            }
            return ',';
        }
    }

    @Override
    public void close() throws IOException {
        if (parser != null) {
            parser.close();
        }
        if (reader != null) {
            reader.close();
        }
    }
}
