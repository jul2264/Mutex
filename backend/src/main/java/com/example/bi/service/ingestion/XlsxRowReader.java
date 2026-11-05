package com.example.bi.service.ingestion;

import com.example.bi.exception.FileValidationException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class XlsxRowReader implements RowReader {

    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "order_id", "order_date", "product_name", "category", "region", "quantity", "unit_price", "unit_cost"
    );

    private static final RawRow POISON_ROW = new RawRow(-1, Map.of(), true);

    private final OPCPackage opcPackage;
    private final InputStream sheetInputStream;
    private final BlockingQueue<RawRow> rowQueue = new ArrayBlockingQueue<>(1000);
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final Thread producerThread;

    private List<String> headerNames = List.of();
    private List<String> extraColumns = List.of();
    private RawRow peekedRow = null;
    private boolean closed = false;

    public XlsxRowReader(File file, int maxRows, int maxColumns) throws IOException {
        ZipSecureFile.setMinInflateRatio(0.01);
        try {
            this.opcPackage = OPCPackage.open(file, PackageAccess.READ);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(this.opcPackage);
            XSSFReader xssfReader = new XSSFReader(this.opcPackage);
            StylesTable styles = xssfReader.getStylesTable();

            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (!sheetIterator.hasNext()) {
                throw new FileValidationException("FILE_EMPTY", "Excel workbook has no sheets");
            }

            this.sheetInputStream = sheetIterator.next();

            // Start background SAX streaming parser
            this.producerThread = new Thread(() -> {
                try {
                    XMLReader xmlReader = XMLHelper.newXMLReader();
                    SheetHandler sheetHandler = new SheetHandler(rowQueue, maxRows, maxColumns);
                    DataFormatter customFormatter = new XlsxDataFormatter();
                    XSSFSheetXMLHandler xmlHandler = new XSSFSheetXMLHandler(styles, null, strings, sheetHandler, customFormatter, false);

                    xmlReader.setContentHandler(xmlHandler);
                    xmlReader.parse(new InputSource(sheetInputStream));

                    if (!sheetHandler.hasHeader) {
                        throw new FileValidationException("FILE_EMPTY", "Excel worksheet has no header row");
                    }
                } catch (Throwable t) {
                    errorRef.set(t);
                } finally {
                    try {
                        rowQueue.put(POISON_ROW);
                    } catch (InterruptedException ignored) {}
                }
            }, "xlsx-sax-parser-" + file.getName());
            this.producerThread.setDaemon(true);
            this.producerThread.start();

            // Read the first item to initialize headers
            advance();

        } catch (Throwable t) {
            close();
            if (t instanceof IOException e) {
                throw e;
            }
            if (t instanceof RuntimeException e) {
                throw e;
            }
            throw new RuntimeException(t);
        }
    }

    private void advance() {
        if (peekedRow != null) {
            return;
        }
        try {
            RawRow row = rowQueue.take();
            if (row == POISON_ROW) {
                Throwable t = errorRef.get();
                if (t != null) {
                    if (t instanceof FileValidationException fve) {
                        throw fve;
                    }
                    if (t instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(t);
                }
                peekedRow = null;
            } else {
                peekedRow = row;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted reading XLSX row", e);
        }
    }

    @Override
    public boolean hasNext() {
        advance();
        return peekedRow != null;
    }

    @Override
    public RawRow nextRow() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        RawRow result = peekedRow;
        peekedRow = null;
        return result;
    }

    @Override
    public List<String> getHeaderNames() {
        return headerNames;
    }

    @Override
    public List<String> getExtraColumns() {
        return extraColumns;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;

        if (producerThread != null && producerThread.isAlive()) {
            producerThread.interrupt();
        }
        if (sheetInputStream != null) {
            try { sheetInputStream.close(); } catch (Exception ignored) {}
        }
        if (opcPackage != null) {
            try { opcPackage.close(); } catch (Exception ignored) {}
        }
    }

    private class SheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final BlockingQueue<RawRow> queue;
        private final int maxRows;
        private final int maxColumns;

        private Map<Integer, String> currentRowCells = new HashMap<>();
        private boolean hasHeader = false;
        private int dataRowsCount = 0;

        SheetHandler(BlockingQueue<RawRow> queue, int maxRows, int maxColumns) {
            this.queue = queue;
            this.maxRows = maxRows;
            this.maxColumns = maxColumns;
        }

        @Override
        public void startRow(int rowNum) {
            currentRowCells = new HashMap<>();
        }

        @Override
        public void endRow(int rowNum) {
            int displayRowNumber = rowNum + 1; // 1-indexed

            if (!hasHeader) {
                // Header row
                if (currentRowCells.isEmpty()) {
                    return; // Skip leading blank lines before header
                }

                int maxColIndex = currentRowCells.keySet().stream().max(Integer::compareTo).orElse(-1);
                if (maxColIndex + 1 > maxColumns) {
                    throw new FileValidationException("TOO_MANY_COLUMNS", String.format("Excel worksheet has %d columns, exceeding limit of %d", maxColIndex + 1, maxColumns));
                }

                List<String> headers = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (int i = 0; i <= maxColIndex; i++) {
                    String raw = currentRowCells.get(i);
                    String norm = CsvRowReader.normalizeHeader(raw);
                    if (norm.isEmpty()) {
                        norm = "column_" + (i + 1);
                    }
                    if (!seen.add(norm)) {
                        throw new FileValidationException("DUPLICATE_HEADER", "Duplicate column name: " + norm);
                    }
                    headers.add(norm);
                }

                List<String> missing = REQUIRED_COLUMNS.stream()
                        .filter(c -> !seen.contains(c))
                        .sorted()
                        .toList();

                if (!missing.isEmpty()) {
                    throw new FileValidationException("MISSING_COLUMN", "Missing required column(s): " + String.join(", ", missing));
                }

                headerNames = Collections.unmodifiableList(headers);
                extraColumns = headers.stream().filter(h -> !REQUIRED_COLUMNS.contains(h)).toList();
                hasHeader = true;
                return;
            }

            // Data row
            boolean isBlank = true;
            Map<String, String> cellMap = new HashMap<>();

            for (int i = 0; i < headerNames.size(); i++) {
                String val = currentRowCells.get(i);
                if (val != null && !val.strip().isEmpty()) {
                    isBlank = false;
                }
                cellMap.put(headerNames.get(i), val);
            }

            if (!isBlank) {
                dataRowsCount++;
                if (dataRowsCount > maxRows) {
                    throw new FileValidationException("TOO_MANY_ROWS", String.format("Excel file exceeds maximum allowed limit of %d rows", maxRows));
                }
            }

            try {
                queue.put(new RawRow(displayRowNumber, cellMap, isBlank));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void cell(String cellAddress, String formattedValue, XSSFComment comment) {
            if (cellAddress == null) return;
            CellReference cellRef = new CellReference(cellAddress);
            int col = cellRef.getCol();
            currentRowCells.put(col, formattedValue);
        }
    }

    private static class XlsxDataFormatter extends DataFormatter {
        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString, boolean use1904windowing) {
            if (DateUtil.isADateFormat(formatIndex, formatString)) {
                try {
                    LocalDate date = DateUtil.getLocalDateTime(value, use1904windowing).toLocalDate();
                    return date.toString(); // ISO yyyy-MM-dd
                } catch (Exception ignored) {}
            }
            // If whole integer (e.g. 10.0 or order_id 123.0)
            if (value == (long) value) {
                return String.valueOf((long) value);
            }
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
    }
}
