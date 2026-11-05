package com.example.bi.service.report;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Streams filtered sales records to CSV format.
 * Features: UTF-8 with BOM, CRLF line endings, header row, FormulaSafe applied strictly to text columns only.
 */
public class CsvExportWriter {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String[] HEADERS = {
            "order_id",
            "order_date",
            "product_name",
            "category",
            "region",
            "quantity",
            "unit_price",
            "unit_cost",
            "revenue",
            "gross_profit"
    };

    private final OutputStream outputStream;
    private final CSVPrinter printer;

    public CsvExportWriter(OutputStream outputStream) throws IOException {
        this.outputStream = outputStream;
        // Write UTF-8 BOM
        outputStream.write(UTF8_BOM);

        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setRecordSeparator("\r\n")
                .setHeader(HEADERS)
                .build();
        this.printer = new CSVPrinter(writer, format);
    }

    public void writeRow(ResultSet rs) throws SQLException, IOException {
        String orderId = rs.getString("order_id");
        Date orderDate = rs.getDate("order_date");
        String productName = rs.getString("product_name");
        String category = rs.getString("category");
        String region = rs.getString("region");
        long quantity = rs.getLong("quantity");
        BigDecimal unitPrice = rs.getBigDecimal("unit_price");
        BigDecimal unitCost = rs.getBigDecimal("unit_cost");
        BigDecimal revenue = rs.getBigDecimal("line_revenue");
        BigDecimal grossProfit = rs.getBigDecimal("line_gross_profit");

        printer.printRecord(
                FormulaSafe.sanitize(orderId),
                orderDate != null ? orderDate.toString() : "",
                FormulaSafe.sanitize(productName),
                FormulaSafe.sanitize(category),
                FormulaSafe.sanitize(region),
                quantity,
                unitPrice != null ? unitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00",
                unitCost != null ? unitCost.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00",
                revenue != null ? revenue.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00",
                grossProfit != null ? grossProfit.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00"
        );
    }

    public void flush() throws IOException {
        printer.flush();
    }
}
