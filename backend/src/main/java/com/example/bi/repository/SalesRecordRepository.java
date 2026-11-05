package com.example.bi.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class SalesRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public SalesRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record DatasetMetrics(int rowCount, LocalDate minDate, LocalDate maxDate) {}

    public record CommittedRowData(
            long id,
            Integer sourceRowNumber,
            String orderId,
            LocalDate orderDate,
            String productName,
            String category,
            String region,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal unitCost
    ) {}

    @Transactional
    public int insertFromStaging(UUID datasetId, UUID importJobId, boolean includeDuplicates) {
        String statusClause = includeDuplicates
                ? "IN ('VALID', 'DUPLICATE_CANDIDATE')"
                : "= 'VALID'";

        String sql = """
                INSERT INTO sales_records (
                    dataset_id, import_job_id, source_row_number, order_id, order_date,
                    product_name, category, region, quantity, unit_price, unit_cost, row_hash
                )
                SELECT
                    ?, ?, source_row_number, order_id, order_date,
                    product_name, category, region, quantity, unit_price, unit_cost, row_hash
                FROM import_staging_rows
                WHERE import_job_id = ?
                  AND row_status """ + statusClause + """
                ORDER BY source_row_number ASC, id ASC
                """;

        return jdbcTemplate.update(sql, datasetId, importJobId, importJobId);
    }

    public DatasetMetrics calculateDatasetMetrics(UUID datasetId) {
        String sql = """
                SELECT COUNT(*) as row_count,
                       MIN(order_date) as min_date,
                       MAX(order_date) as max_date
                FROM sales_records
                WHERE dataset_id = ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            int count = rs.getInt("row_count");
            Date minD = rs.getDate("min_date");
            Date maxD = rs.getDate("max_date");
            LocalDate minDate = (minD != null) ? minD.toLocalDate() : null;
            LocalDate maxDate = (maxD != null) ? maxD.toLocalDate() : null;
            return new DatasetMetrics(count, minDate, maxDate);
        }, datasetId);
    }

    public List<CommittedRowData> findCommittedRows(UUID datasetId, int offset, int limit) {
        String sql = """
                SELECT id, source_row_number, order_id, order_date,
                       product_name, category, region, quantity, unit_price, unit_cost
                FROM sales_records
                WHERE dataset_id = ?
                ORDER BY id ASC
                LIMIT ? OFFSET ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date d = rs.getDate("order_date");
            LocalDate orderDate = (d != null) ? d.toLocalDate() : null;
            int srn = rs.getInt("source_row_number");
            Integer sourceRowNumber = rs.wasNull() ? null : srn;

            return new CommittedRowData(
                    rs.getLong("id"),
                    sourceRowNumber,
                    rs.getString("order_id"),
                    orderDate,
                    rs.getString("product_name"),
                    rs.getString("category"),
                    rs.getString("region"),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("unit_cost")
            );
        }, datasetId, limit, offset);
    }

    public long countCommittedRows(UUID datasetId) {
        String sql = "SELECT COUNT(*) FROM sales_records WHERE dataset_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, datasetId);
        return count != null ? count : 0L;
    }
}

