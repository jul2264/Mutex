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

    @Transactional
    public void deleteStagingData(UUID importJobId) {
        jdbcTemplate.update("DELETE FROM import_row_issues WHERE import_job_id = ?", importJobId);
        jdbcTemplate.update("DELETE FROM import_staging_rows WHERE import_job_id = ?", importJobId);
    }
}

