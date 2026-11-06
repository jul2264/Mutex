package com.example.bi.repository;

import com.example.bi.domain.IssueSeverity;
import com.example.bi.domain.RowStatus;
import com.example.bi.service.ingestion.ParsedRow;
import com.example.bi.service.ingestion.RowIssue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.*;

@Repository
public class StagingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StagingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public record StagingCounts(int validRows, int invalidRows, int duplicateRows, int warningCount) {}

    @Transactional
    public void saveStagingRows(UUID importJobId, List<ParsedRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO import_staging_rows (
                    import_job_id, source_row_number, row_status, raw_values,
                    order_id, order_date, product_name, category, region, quantity,
                    unit_price, unit_cost, row_hash
                ) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, rows, rows.size(), (PreparedStatement ps, ParsedRow row) -> {
            ps.setObject(1, importJobId);
            ps.setInt(2, row.sourceRowNumber());
            ps.setString(3, row.status().name());

            if (row.rawValues() != null && !row.rawValues().isEmpty()) {
                try {
                    ps.setString(4, objectMapper.writeValueAsString(row.rawValues()));
                } catch (Exception e) {
                    ps.setString(4, "{}");
                }
            } else {
                ps.setNull(4, Types.VARCHAR);
            }

            ps.setString(5, row.orderId());
            if (row.orderDate() != null) {
                ps.setDate(6, Date.valueOf(row.orderDate()));
            } else {
                ps.setNull(6, Types.DATE);
            }
            ps.setString(7, row.productName());
            ps.setString(8, row.category());
            ps.setString(9, row.region());
            if (row.quantity() != null) {
                ps.setInt(10, row.quantity());
            } else {
                ps.setNull(10, Types.INTEGER);
            }
            ps.setBigDecimal(11, row.unitPrice());
            ps.setBigDecimal(12, row.unitCost());
            ps.setString(13, row.rowHash());
        });
    }

    @Transactional
    public void saveIssues(UUID importJobId, List<RowIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO import_row_issues (
                    import_job_id, source_row_number, column_name, severity, code, message, raw_value
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, issues, issues.size(), (PreparedStatement ps, RowIssue issue) -> {
            ps.setObject(1, importJobId);
            ps.setInt(2, issue.sourceRowNumber());
            ps.setString(3, issue.columnName());
            ps.setString(4, issue.severity().name());
            ps.setString(5, issue.code());
            ps.setString(6, issue.message());
            ps.setString(7, issue.rawValue());
        });
    }

    @Transactional
    public int markDuplicates(UUID importJobId) {
        // Find rows that have same hash as an earlier row in the same import job
        String updateSql = """
                WITH ranked AS (
                    SELECT id, row_hash, source_row_number,
                           ROW_NUMBER() OVER (PARTITION BY row_hash ORDER BY source_row_number ASC) as rn,
                           FIRST_VALUE(source_row_number) OVER (PARTITION BY row_hash ORDER BY source_row_number ASC) as first_row
                    FROM import_staging_rows
                    WHERE import_job_id = ? AND row_status = 'VALID' AND row_hash IS NOT NULL
                )
                UPDATE import_staging_rows s
                SET row_status = 'DUPLICATE_CANDIDATE'
                FROM ranked r
                WHERE s.id = r.id AND r.rn > 1
                """;

        int duplicatesMarked = jdbcTemplate.update(updateSql, importJobId);

        if (duplicatesMarked > 0) {
            String insertIssuesSql = """
                    WITH ranked AS (
                        SELECT id, row_hash, source_row_number,
                               ROW_NUMBER() OVER (PARTITION BY row_hash ORDER BY source_row_number ASC) as rn,
                               FIRST_VALUE(source_row_number) OVER (PARTITION BY row_hash ORDER BY source_row_number ASC) as first_row
                        FROM import_staging_rows
                        WHERE import_job_id = ? AND row_hash IS NOT NULL
                    )
                    INSERT INTO import_row_issues (import_job_id, source_row_number, column_name, severity, code, message, raw_value)
                    SELECT ?, source_row_number, 'order_id', 'WARNING', 'DUPLICATE_IN_FILE',
                           'Exact duplicate of row ' || first_row, NULL
                    FROM ranked
                    WHERE rn > 1
                    """;
            jdbcTemplate.update(insertIssuesSql, importJobId, importJobId);
        }

        return duplicatesMarked;
    }

    @Transactional
    public int deleteDuplicateCandidates(UUID importJobId) {
        String deleteIssuesSql = """
                DELETE FROM import_row_issues
                WHERE import_job_id = ?
                  AND source_row_number IN (
                      SELECT source_row_number
                      FROM import_staging_rows
                      WHERE import_job_id = ? AND row_status = 'DUPLICATE_CANDIDATE'
                  )
                """;
        jdbcTemplate.update(deleteIssuesSql, importJobId, importJobId);

        String deleteRowsSql = """
                DELETE FROM import_staging_rows
                WHERE import_job_id = ? AND row_status = 'DUPLICATE_CANDIDATE'
                """;
        return jdbcTemplate.update(deleteRowsSql, importJobId);
    }

    public StagingCounts calculateCounts(UUID importJobId) {
        String statusSql = """
                SELECT row_status, COUNT(*) as cnt
                FROM import_staging_rows
                WHERE import_job_id = ?
                GROUP BY row_status
                """;

        Map<String, Integer> statusMap = new HashMap<>();
        jdbcTemplate.query(statusSql, rs -> {
            statusMap.put(rs.getString("row_status"), rs.getInt("cnt"));
        }, importJobId);

        String warningSql = """
                SELECT COUNT(*)
                FROM import_row_issues
                WHERE import_job_id = ? AND severity = 'WARNING'
                """;
        Integer warnings = jdbcTemplate.queryForObject(warningSql, Integer.class, importJobId);

        return new StagingCounts(
                statusMap.getOrDefault(RowStatus.VALID.name(), 0),
                statusMap.getOrDefault(RowStatus.INVALID.name(), 0),
                statusMap.getOrDefault(RowStatus.DUPLICATE_CANDIDATE.name(), 0),
                warnings != null ? warnings : 0
        );
    }

    public List<RowIssue> findIssuesByJobId(UUID importJobId) {
        String sql = """
                SELECT source_row_number, column_name, severity, code, message, raw_value
                FROM import_row_issues
                WHERE import_job_id = ?
                ORDER BY source_row_number ASC, id ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RowIssue(
                rs.getInt("source_row_number"),
                rs.getString("column_name"),
                IssueSeverity.valueOf(rs.getString("severity")),
                rs.getString("code"),
                rs.getString("message"),
                rs.getString("raw_value")
        ), importJobId);
    }

    public record StagingRowData(
            long id,
            int sourceRowNumber,
            RowStatus rowStatus,
            Map<String, Object> rawValues,
            String orderId,
            java.time.LocalDate orderDate,
            String productName,
            String category,
            String region,
            Integer quantity,
            java.math.BigDecimal unitPrice,
            java.math.BigDecimal unitCost
    ) {}

    public List<StagingRowData> findStagingRows(UUID importJobId, RowStatus statusFilter, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, source_row_number, row_status, raw_values,
                       order_id, order_date, product_name, category, region,
                       quantity, unit_price, unit_cost
                FROM import_staging_rows
                WHERE import_job_id = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(importJobId);

        if (statusFilter != null) {
            sql.append(" AND row_status = ?");
            params.add(statusFilter.name());
        }

        sql.append(" ORDER BY source_row_number ASC, id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> rawMap = null;
            String rawJson = rs.getString("raw_values");
            if (rawJson != null && !rawJson.isBlank()) {
                try {
                    rawMap = objectMapper.readValue(rawJson, Map.class);
                } catch (Exception ignored) {}
            }

            java.sql.Date d = rs.getDate("order_date");
            java.time.LocalDate localDate = (d != null) ? d.toLocalDate() : null;

            int qty = rs.getInt("quantity");
            Integer quantity = rs.wasNull() ? null : qty;

            return new StagingRowData(
                    rs.getLong("id"),
                    rs.getInt("source_row_number"),
                    RowStatus.valueOf(rs.getString("row_status")),
                    rawMap,
                    rs.getString("order_id"),
                    localDate,
                    rs.getString("product_name"),
                    rs.getString("category"),
                    rs.getString("region"),
                    quantity,
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("unit_cost")
            );
        }, params.toArray());
    }

    public long countStagingRows(UUID importJobId, RowStatus statusFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM import_staging_rows WHERE import_job_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(importJobId);

        if (statusFilter != null) {
            sql.append(" AND row_status = ?");
            params.add(statusFilter.name());
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public Map<Integer, List<RowIssue>> findIssuesForRows(UUID importJobId, List<Integer> rowNumbers) {
        if (rowNumbers == null || rowNumbers.isEmpty()) {
            return Collections.emptyMap();
        }

        String inSql = String.join(",", Collections.nCopies(rowNumbers.size(), "?"));
        String sql = "SELECT source_row_number, column_name, severity, code, message, raw_value " +
                     "FROM import_row_issues WHERE import_job_id = ? AND source_row_number IN (" + inSql + ") " +
                     "ORDER BY source_row_number ASC, id ASC";

        List<Object> params = new ArrayList<>();
        params.add(importJobId);
        params.addAll(rowNumbers);

        Map<Integer, List<RowIssue>> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            int rowNum = rs.getInt("source_row_number");
            RowIssue issue = new RowIssue(
                    rowNum,
                    rs.getString("column_name"),
                    IssueSeverity.valueOf(rs.getString("severity")),
                    rs.getString("code"),
                    rs.getString("message"),
                    rs.getString("raw_value")
            );
            result.computeIfAbsent(rowNum, k -> new ArrayList<>()).add(issue);
        }, params.toArray());

        return result;
    }

    public Optional<StagingRowData> findStagingRow(UUID importJobId, int sourceRowNumber) {
        String sql = """
                SELECT id, source_row_number, row_status, raw_values,
                       order_id, order_date, product_name, category, region,
                       quantity, unit_price, unit_cost
                FROM import_staging_rows
                WHERE import_job_id = ? AND source_row_number = ?
                """;

        List<StagingRowData> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> rawMap = null;
            String rawJson = rs.getString("raw_values");
            if (rawJson != null && !rawJson.isBlank()) {
                try {
                    rawMap = objectMapper.readValue(rawJson, Map.class);
                } catch (Exception ignored) {}
            }

            java.sql.Date d = rs.getDate("order_date");
            java.time.LocalDate localDate = (d != null) ? d.toLocalDate() : null;

            int qty = rs.getInt("quantity");
            Integer quantity = rs.wasNull() ? null : qty;

            return new StagingRowData(
                    rs.getLong("id"),
                    rs.getInt("source_row_number"),
                    RowStatus.valueOf(rs.getString("row_status")),
                    rawMap,
                    rs.getString("order_id"),
                    localDate,
                    rs.getString("product_name"),
                    rs.getString("category"),
                    rs.getString("region"),
                    quantity,
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("unit_cost")
            );
        }, importJobId, sourceRowNumber);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Transactional
    public void updateStagingRow(UUID importJobId, ParsedRow row) {
        String sql = """
                UPDATE import_staging_rows
                SET row_status = ?,
                    raw_values = ?::jsonb,
                    order_id = ?,
                    order_date = ?,
                    product_name = ?,
                    category = ?,
                    region = ?,
                    quantity = ?,
                    unit_price = ?,
                    unit_cost = ?,
                    row_hash = ?
                WHERE import_job_id = ? AND source_row_number = ?
                """;

        jdbcTemplate.update(sql, ps -> {
            ps.setString(1, row.status().name());
            if (row.rawValues() != null && !row.rawValues().isEmpty()) {
                try {
                    ps.setString(2, objectMapper.writeValueAsString(row.rawValues()));
                } catch (Exception e) {
                    ps.setString(2, "{}");
                }
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, row.orderId());
            if (row.orderDate() != null) {
                ps.setDate(4, Date.valueOf(row.orderDate()));
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setString(5, row.productName());
            ps.setString(6, row.category());
            ps.setString(7, row.region());
            if (row.quantity() != null) {
                ps.setInt(8, row.quantity());
            } else {
                ps.setNull(8, Types.INTEGER);
            }
            ps.setBigDecimal(9, row.unitPrice());
            ps.setBigDecimal(10, row.unitCost());
            ps.setString(11, row.rowHash());
            ps.setObject(12, importJobId);
            ps.setInt(13, row.sourceRowNumber());
        });
    }

    @Transactional
    public void deleteIssuesForRow(UUID importJobId, int sourceRowNumber) {
        jdbcTemplate.update(
                "DELETE FROM import_row_issues WHERE import_job_id = ? AND source_row_number = ?",
                importJobId, sourceRowNumber
        );
    }

    @Transactional
    public void resetDuplicateCandidates(UUID importJobId) {
        jdbcTemplate.update(
                "DELETE FROM import_row_issues WHERE import_job_id = ? AND code = 'DUPLICATE_IN_FILE'",
                importJobId
        );
        jdbcTemplate.update(
                "UPDATE import_staging_rows SET row_status = 'VALID' WHERE import_job_id = ? AND row_status = 'DUPLICATE_CANDIDATE'",
                importJobId
        );
    }

    @Transactional
    public void deleteStagingData(UUID importJobId) {
        jdbcTemplate.update("DELETE FROM import_row_issues WHERE import_job_id = ?", importJobId);
        jdbcTemplate.update("DELETE FROM import_staging_rows WHERE import_job_id = ?", importJobId);
    }

    public java.time.LocalDate findLatestValidOrderDate(UUID importJobId) {
        String sql = """
                SELECT MAX(order_date)
                FROM import_staging_rows
                WHERE import_job_id = ? AND row_status = 'VALID' AND order_date IS NOT NULL
                """;
        java.sql.Date maxDate = jdbcTemplate.queryForObject(sql, java.sql.Date.class, importJobId);
        return maxDate != null ? maxDate.toLocalDate() : null;
    }

    public List<StagingRowData> findInvalidStagingRows(UUID importJobId) {
        String sql = """
                SELECT id, source_row_number, row_status, raw_values,
                       order_id, order_date, product_name, category, region,
                       quantity, unit_price, unit_cost
                FROM import_staging_rows
                WHERE import_job_id = ? AND row_status = 'INVALID'
                ORDER BY source_row_number ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> rawMap = null;
            String rawJson = rs.getString("raw_values");
            if (rawJson != null && !rawJson.isBlank()) {
                try {
                    rawMap = objectMapper.readValue(rawJson, Map.class);
                } catch (Exception ignored) {}
            }

            java.sql.Date d = rs.getDate("order_date");
            java.time.LocalDate localDate = (d != null) ? d.toLocalDate() : null;

            int qty = rs.getInt("quantity");
            Integer quantity = rs.wasNull() ? null : qty;

            return new StagingRowData(
                    rs.getLong("id"),
                    rs.getInt("source_row_number"),
                    RowStatus.valueOf(rs.getString("row_status")),
                    rawMap,
                    rs.getString("order_id"),
                    localDate,
                    rs.getString("product_name"),
                    rs.getString("category"),
                    rs.getString("region"),
                    quantity,
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("unit_cost")
            );
        }, importJobId);
    }

    @Transactional
    public void batchUpdateStagingRows(UUID importJobId, List<ParsedRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = """
                UPDATE import_staging_rows
                SET row_status = ?,
                    raw_values = ?::jsonb,
                    order_id = ?,
                    order_date = ?,
                    product_name = ?,
                    category = ?,
                    region = ?,
                    quantity = ?,
                    unit_price = ?,
                    unit_cost = ?,
                    row_hash = ?
                WHERE import_job_id = ? AND source_row_number = ?
                """;

        jdbcTemplate.batchUpdate(sql, rows, rows.size(), (PreparedStatement ps, ParsedRow row) -> {
            ps.setString(1, row.status().name());
            if (row.rawValues() != null && !row.rawValues().isEmpty()) {
                try {
                    ps.setString(2, objectMapper.writeValueAsString(row.rawValues()));
                } catch (Exception e) {
                    ps.setString(2, "{}");
                }
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, row.orderId());
            if (row.orderDate() != null) {
                ps.setDate(4, Date.valueOf(row.orderDate()));
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setString(5, row.productName());
            ps.setString(6, row.category());
            ps.setString(7, row.region());
            if (row.quantity() != null) {
                ps.setInt(8, row.quantity());
            } else {
                ps.setNull(8, Types.INTEGER);
            }
            ps.setBigDecimal(9, row.unitPrice());
            ps.setBigDecimal(10, row.unitCost());
            ps.setString(11, row.rowHash());
            ps.setObject(12, importJobId);
            ps.setInt(13, row.sourceRowNumber());
        });
    }

    @Transactional
    public void batchReplaceIssues(UUID importJobId, List<Integer> rowNumbers, List<RowIssue> newIssues) {
        if (rowNumbers != null && !rowNumbers.isEmpty()) {
            String inSql = String.join(",", Collections.nCopies(rowNumbers.size(), "?"));
            String deleteSql = "DELETE FROM import_row_issues WHERE import_job_id = ? AND source_row_number IN (" + inSql + ")";
            List<Object> params = new ArrayList<>();
            params.add(importJobId);
            params.addAll(rowNumbers);
            jdbcTemplate.update(deleteSql, params.toArray());
        }

        if (newIssues != null && !newIssues.isEmpty()) {
            saveIssues(importJobId, newIssues);
        }
    }
}

