package com.example.bi.service.report;

import com.example.bi.domain.Dataset;
import com.example.bi.dto.*;
import com.example.bi.service.analytics.AnalyticsFilter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Streams filtered records or generates multi-sheet summary workbooks using Apache POI SXSSFWorkbook.
 * Ensures temporary files are disposed cleanly upon completion.
 */
public class XlsxExportWriter implements Closeable {

    private final SXSSFWorkbook workbook;
    private final DataFormat dataFormat;
    private final CellStyle headerStyle;
    private final CellStyle moneyStyle;
    private final CellStyle intStyle;
    private final CellStyle percentStyle;
    private final CellStyle textStyle;

    private SXSSFSheet recordsSheet;
    private int recordsRowIdx = 0;

    public XlsxExportWriter() {
        // Keep 100 rows in memory, flushing excess to temp storage
        this.workbook = new SXSSFWorkbook(100);
        this.dataFormat = workbook.createDataFormat();

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        this.headerStyle = workbook.createCellStyle();
        this.headerStyle.setFont(boldFont);
        this.headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        this.headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        this.headerStyle.setBorderBottom(BorderStyle.THIN);
        this.headerStyle.setBorderTop(BorderStyle.THIN);
        this.headerStyle.setBorderLeft(BorderStyle.THIN);
        this.headerStyle.setBorderRight(BorderStyle.THIN);

        this.moneyStyle = workbook.createCellStyle();
        this.moneyStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

        this.intStyle = workbook.createCellStyle();
        this.intStyle.setDataFormat(dataFormat.getFormat("#,##0"));

        this.percentStyle = workbook.createCellStyle();
        this.percentStyle.setDataFormat(dataFormat.getFormat("0.00%"));

        this.textStyle = workbook.createCellStyle();
    }

    public void initRecordsSheet() {
        this.recordsSheet = workbook.createSheet("Records");
        this.recordsSheet.trackAllColumnsForAutoSizing();

        Row headerRow = recordsSheet.createRow(recordsRowIdx++);
        String[] headers = {
                "Order ID", "Order Date", "Product Name", "Category", "Region",
                "Quantity", "Unit Price", "Unit Cost", "Revenue", "Gross Profit"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i, CellType.STRING);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    public void writeRecordRow(ResultSet rs) throws SQLException {
        if (recordsSheet == null) {
            initRecordsSheet();
        }
        Row row = recordsSheet.createRow(recordsRowIdx++);

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

        // Order ID (Text, FormulaSafe)
        Cell c0 = row.createCell(0, CellType.STRING);
        c0.setCellValue(FormulaSafe.sanitize(orderId));

        // Order Date (Text ISO)
        Cell c1 = row.createCell(1, CellType.STRING);
        c1.setCellValue(orderDate != null ? orderDate.toString() : "");

        // Product Name (Text, FormulaSafe)
        Cell c2 = row.createCell(2, CellType.STRING);
        c2.setCellValue(FormulaSafe.sanitize(productName));

        // Category (Text, FormulaSafe)
        Cell c3 = row.createCell(3, CellType.STRING);
        c3.setCellValue(FormulaSafe.sanitize(category));

        // Region (Text, FormulaSafe)
        Cell c4 = row.createCell(4, CellType.STRING);
        c4.setCellValue(FormulaSafe.sanitize(region));

        // Quantity (Numeric integer)
        Cell c5 = row.createCell(5, CellType.NUMERIC);
        c5.setCellValue(quantity);
        c5.setCellStyle(intStyle);

        // Unit Price (Numeric decimal)
        Cell c6 = row.createCell(6, CellType.NUMERIC);
        c6.setCellValue(unitPrice != null ? unitPrice.doubleValue() : 0.0);
        c6.setCellStyle(moneyStyle);

        // Unit Cost (Numeric decimal)
        Cell c7 = row.createCell(7, CellType.NUMERIC);
        c7.setCellValue(unitCost != null ? unitCost.doubleValue() : 0.0);
        c7.setCellStyle(moneyStyle);

        // Revenue (Numeric decimal)
        Cell c8 = row.createCell(8, CellType.NUMERIC);
        c8.setCellValue(revenue != null ? revenue.doubleValue() : 0.0);
        c8.setCellStyle(moneyStyle);

        // Gross Profit (Numeric decimal)
        Cell c9 = row.createCell(9, CellType.NUMERIC);
        c9.setCellValue(grossProfit != null ? grossProfit.doubleValue() : 0.0);
        c9.setCellStyle(moneyStyle);
    }

    public void writeSummaryWorkbook(
            Dataset dataset,
            AnalyticsFilter filter,
            SummaryResponse summary,
            TrendsResponse trends,
            CategoriesResponse categories,
            RegionsResponse regions,
            TopProductsResponse topProducts
    ) {
        buildSummarySheet(dataset, filter, summary);
        buildTrendsSheet(trends);
        buildCategoriesSheet(categories);
        buildRegionsSheet(regions);
        buildTopProductsSheet(topProducts);
    }

    private void buildSummarySheet(Dataset dataset, AnalyticsFilter filter, SummaryResponse summary) {
        SXSSFSheet sheet = workbook.createSheet("Summary");
        int rowIdx = 0;

        // Title
        Row titleRow = sheet.createRow(rowIdx++);
        Cell titleCell = titleRow.createCell(0, CellType.STRING);
        titleCell.setCellValue("Mutex Business Intelligence — KPI Summary Report");
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        rowIdx++; // blank row

        // Metadata section
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Dataset Name: " + dataset.getName());
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Currency: " + dataset.getCurrencyCode());
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Generated At: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        String dateRange = (filter.from() != null ? filter.from().toString() : "All") + " to " + (filter.to() != null ? filter.to().toString() : "All");
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Date Filter: " + dateRange);
        if (filter.categories() != null && !filter.categories().isEmpty()) {
            sheet.createRow(rowIdx++).createCell(0).setCellValue("Categories Filter: " + String.join(", ", filter.categories()));
        }
        if (filter.regions() != null && !filter.regions().isEmpty()) {
            sheet.createRow(rowIdx++).createCell(0).setCellValue("Regions Filter: " + String.join(", ", filter.regions()));
        }
        if (filter.product() != null && !filter.product().isBlank()) {
            sheet.createRow(rowIdx++).createCell(0).setCellValue("Product Search: " + filter.product());
        }

        rowIdx++; // blank row

        // KPI Table
        Row tableHeader = sheet.createRow(rowIdx++);
        String[] headers = {"KPI Metric", "Value", "Definition / Notes"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = tableHeader.createCell(i, CellType.STRING);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        // Revenue
        Row rRev = sheet.createRow(rowIdx++);
        rRev.createCell(0).setCellValue("Total Revenue");
        Cell cRevVal = rRev.createCell(1, CellType.NUMERIC);
        cRevVal.setCellValue(summary.revenue() != null ? Double.parseDouble(summary.revenue()) : 0.0);
        cRevVal.setCellStyle(moneyStyle);
        rRev.createCell(2).setCellValue("Σ quantity × unit_price");

        // Gross Profit
        Row rGp = sheet.createRow(rowIdx++);
        rGp.createCell(0).setCellValue("Gross Profit");
        Cell cGpVal = rGp.createCell(1, CellType.NUMERIC);
        cGpVal.setCellValue(summary.grossProfit() != null ? Double.parseDouble(summary.grossProfit()) : 0.0);
        cGpVal.setCellStyle(moneyStyle);
        rGp.createCell(2).setCellValue("Σ quantity × (unit_price − unit_cost)");

        // Gross Margin
        Row rMargin = sheet.createRow(rowIdx++);
        rMargin.createCell(0).setCellValue("Gross Margin %");
        if (summary.grossMarginPct() != null && summary.grossMarginPct().value() != null) {
            Cell cMarginVal = rMargin.createCell(1, CellType.NUMERIC);
            cMarginVal.setCellValue(Double.parseDouble(summary.grossMarginPct().value()) / 100.0);
            cMarginVal.setCellStyle(percentStyle);
        } else {
            rMargin.createCell(1).setCellValue("N/A (" + (summary.grossMarginPct() != null ? summary.grossMarginPct().unavailableReason() : "ZERO_REVENUE") + ")");
        }
        rMargin.createCell(2).setCellValue("gross profit ÷ revenue × 100");

        // Units Sold
        Row rUnits = sheet.createRow(rowIdx++);
        rUnits.createCell(0).setCellValue("Units Sold");
        Cell cUnitsVal = rUnits.createCell(1, CellType.NUMERIC);
        cUnitsVal.setCellValue(summary.unitsSold());
        cUnitsVal.setCellStyle(intStyle);
        rUnits.createCell(2).setCellValue("Σ quantity");

        // Orders
        Row rOrders = sheet.createRow(rowIdx++);
        rOrders.createCell(0).setCellValue("Orders");
        Cell cOrdersVal = rOrders.createCell(1, CellType.NUMERIC);
        cOrdersVal.setCellValue(summary.orders());
        cOrdersVal.setCellStyle(intStyle);
        rOrders.createCell(2).setCellValue("COUNT(DISTINCT order_id)");

        // Line Items
        Row rLines = sheet.createRow(rowIdx++);
        rLines.createCell(0).setCellValue("Line Items");
        Cell cLinesVal = rLines.createCell(1, CellType.NUMERIC);
        cLinesVal.setCellValue(summary.lineItems());
        cLinesVal.setCellStyle(intStyle);
        rLines.createCell(2).setCellValue("Total sales record count");

        // Revenue Growth % (if available)
        if (summary.revenueGrowthPct() != null) {
            Row rGrowth = sheet.createRow(rowIdx++);
            rGrowth.createCell(0).setCellValue("Revenue Growth %");
            if (summary.revenueGrowthPct().value() != null) {
                Cell cGrowthVal = rGrowth.createCell(1, CellType.NUMERIC);
                cGrowthVal.setCellValue(Double.parseDouble(summary.revenueGrowthPct().value()) / 100.0);
                cGrowthVal.setCellStyle(percentStyle);
            } else {
                rGrowth.createCell(1).setCellValue("N/A (" + summary.revenueGrowthPct().unavailableReason() + ")");
            }
            rGrowth.createCell(2).setCellValue("(current − previous) ÷ previous × 100");
        }
    }

    private void buildTrendsSheet(TrendsResponse trends) {
        SXSSFSheet sheet = workbook.createSheet("Trends");
        int rowIdx = 0;

        Row headerRow = sheet.createRow(rowIdx++);
        String[] headers = {"Month", "Revenue", "Gross Profit", "Gross Margin %", "Units Sold", "MoM Revenue Growth %"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i, CellType.STRING);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        if (trends != null && trends.points() != null) {
            for (TrendPoint tp : trends.points()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(tp.month());

                Cell cRev = row.createCell(1, CellType.NUMERIC);
                cRev.setCellValue(tp.revenue() != null ? Double.parseDouble(tp.revenue()) : 0.0);
                cRev.setCellStyle(moneyStyle);

                Cell cGp = row.createCell(2, CellType.NUMERIC);
                cGp.setCellValue(tp.grossProfit() != null ? Double.parseDouble(tp.grossProfit()) : 0.0);
                cGp.setCellStyle(moneyStyle);

                if (tp.grossMarginPct() != null && tp.grossMarginPct().value() != null) {
                    Cell cMargin = row.createCell(3, CellType.NUMERIC);
                    cMargin.setCellValue(Double.parseDouble(tp.grossMarginPct().value()) / 100.0);
                    cMargin.setCellStyle(percentStyle);
                } else {
                    row.createCell(3).setCellValue("N/A");
                }

                Cell cUnits = row.createCell(4, CellType.NUMERIC);
                cUnits.setCellValue(tp.unitsSold());
                cUnits.setCellStyle(intStyle);

                if (tp.revenueGrowthPct() != null && tp.revenueGrowthPct().value() != null) {
                    Cell cGrowth = row.createCell(5, CellType.NUMERIC);
                    cGrowth.setCellValue(Double.parseDouble(tp.revenueGrowthPct().value()) / 100.0);
                    cGrowth.setCellStyle(percentStyle);
                } else {
                    row.createCell(5).setCellValue("N/A");
                }
            }
        }
    }

    private void buildCategoriesSheet(CategoriesResponse categories) {
        SXSSFSheet sheet = workbook.createSheet("Categories");
        int rowIdx = 0;

        Row headerRow = sheet.createRow(rowIdx++);
        String[] headers = {"Category", "Revenue", "Gross Profit", "Gross Margin %", "Units Sold", "Revenue Share %"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i, CellType.STRING);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        if (categories != null && categories.categories() != null) {
            for (BreakdownRow cr : categories.categories()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(FormulaSafe.sanitize(cr.name()));

                Cell cRev = row.createCell(1, CellType.NUMERIC);
                cRev.setCellValue(cr.revenue() != null ? Double.parseDouble(cr.revenue()) : 0.0);
                cRev.setCellStyle(moneyStyle);

                Cell cGp = row.createCell(2, CellType.NUMERIC);
                cGp.setCellValue(cr.grossProfit() != null ? Double.parseDouble(cr.grossProfit()) : 0.0);
                cGp.setCellStyle(moneyStyle);

                if (cr.grossMarginPct() != null && cr.grossMarginPct().value() != null) {
                    Cell cMargin = row.createCell(3, CellType.NUMERIC);
                    cMargin.setCellValue(Double.parseDouble(cr.grossMarginPct().value()) / 100.0);
                    cMargin.setCellStyle(percentStyle);
                } else {
                    row.createCell(3).setCellValue("N/A");
                }

                Cell cUnits = row.createCell(4, CellType.NUMERIC);
                cUnits.setCellValue(cr.unitsSold());
                cUnits.setCellStyle(intStyle);

                if (cr.revenueSharePct() != null) {
                    Cell cShare = row.createCell(5, CellType.NUMERIC);
                    cShare.setCellValue(Double.parseDouble(cr.revenueSharePct()) / 100.0);
                    cShare.setCellStyle(percentStyle);
                } else {
                    row.createCell(5).setCellValue("N/A");
                }
            }
        }
    }

    private void buildRegionsSheet(RegionsResponse regions) {
        SXSSFSheet sheet = workbook.createSheet("Regions");
        int rowIdx = 0;

        Row headerRow = sheet.createRow(rowIdx++);
        String[] headers = {"Region", "Revenue", "Gross Profit", "Gross Margin %", "Units Sold", "Margin Delta Points"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i, CellType.STRING);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        if (regions != null && regions.regions() != null) {
            for (BreakdownRow rr : regions.regions()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(FormulaSafe.sanitize(rr.name()));

                Cell cRev = row.createCell(1, CellType.NUMERIC);
                cRev.setCellValue(rr.revenue() != null ? Double.parseDouble(rr.revenue()) : 0.0);
                cRev.setCellStyle(moneyStyle);

                Cell cGp = row.createCell(2, CellType.NUMERIC);
                cGp.setCellValue(rr.grossProfit() != null ? Double.parseDouble(rr.grossProfit()) : 0.0);
                cGp.setCellStyle(moneyStyle);

                if (rr.grossMarginPct() != null && rr.grossMarginPct().value() != null) {
                    Cell cMargin = row.createCell(3, CellType.NUMERIC);
                    cMargin.setCellValue(Double.parseDouble(rr.grossMarginPct().value()) / 100.0);
                    cMargin.setCellStyle(percentStyle);
                } else {
                    row.createCell(3).setCellValue("N/A");
                }

                Cell cUnits = row.createCell(4, CellType.NUMERIC);
                cUnits.setCellValue(rr.unitsSold());
                cUnits.setCellStyle(intStyle);

                if (rr.marginDeltaPoints() != null) {
                    Cell cDelta = row.createCell(5, CellType.NUMERIC);
                    cDelta.setCellValue(Double.parseDouble(rr.marginDeltaPoints()));
                    cDelta.setCellStyle(moneyStyle);
                } else {
                    row.createCell(5).setCellValue("N/A");
                }
            }
        }
    }

    private void buildTopProductsSheet(TopProductsResponse topProducts) {
        SXSSFSheet sheet = workbook.createSheet("Top Products");
        int rowIdx = 0;

        Row headerRow = sheet.createRow(rowIdx++);
        String[] headers = {"Product Name", "Revenue", "Gross Profit", "Units Sold", "Revenue Rank", "Profit Rank"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i, CellType.STRING);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        if (topProducts != null && topProducts.products() != null) {
            for (TopProductRow pr : topProducts.products()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(FormulaSafe.sanitize(pr.product()));

                Cell cRev = row.createCell(1, CellType.NUMERIC);
                cRev.setCellValue(pr.revenue() != null ? Double.parseDouble(pr.revenue()) : 0.0);
                cRev.setCellStyle(moneyStyle);

                Cell cGp = row.createCell(2, CellType.NUMERIC);
                cGp.setCellValue(pr.grossProfit() != null ? Double.parseDouble(pr.grossProfit()) : 0.0);
                cGp.setCellStyle(moneyStyle);

                Cell cUnits = row.createCell(3, CellType.NUMERIC);
                cUnits.setCellValue(pr.unitsSold());
                cUnits.setCellStyle(intStyle);

                Cell cRevRank = row.createCell(4, CellType.NUMERIC);
                cRevRank.setCellValue(pr.revenueRank());
                cRevRank.setCellStyle(intStyle);

                Cell cProfitRank = row.createCell(5, CellType.NUMERIC);
                cProfitRank.setCellValue(pr.profitRank());
                cProfitRank.setCellStyle(intStyle);
            }
        }
    }

    public void writeTo(OutputStream os) throws IOException {
        workbook.write(os);
    }

    @Override
    public void close() throws IOException {
        try {
            workbook.dispose();
        } finally {
            workbook.close();
        }
    }
}
