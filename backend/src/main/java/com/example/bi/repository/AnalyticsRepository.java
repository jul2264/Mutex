package com.example.bi.repository;

import com.example.bi.dto.SalesTotals;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SalesTotals totals(UUID datasetId, LocalDate from, LocalDate toExclusive) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                COALESCE(SUM(line_revenue), 0.00) AS revenue,
                COALESCE(SUM(line_gross_profit), 0.00) AS gross_profit,
                COALESCE(SUM(quantity), 0) AS units_sold,
                COUNT(DISTINCT order_id) AS orders,
                COUNT(*) AS line_items
            FROM sales_records
            WHERE dataset_id = :datasetId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("datasetId", datasetId);

        if (from != null) {
            sql.append(" AND order_date >= :from");
            params.addValue("from", from);
        }
        if (toExclusive != null) {
            sql.append(" AND order_date < :toExclusive");
            params.addValue("toExclusive", toExclusive);
        }

        return jdbcTemplate.queryForObject(sql.toString(), params, (rs, rowNum) -> new SalesTotals(
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("gross_profit"),
                rs.getLong("units_sold"),
                rs.getLong("orders"),
                rs.getLong("line_items")
        ));
    }
}

