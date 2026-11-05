package com.example.bi.repository;

import com.example.bi.domain.RankBy;
import com.example.bi.dto.SalesTotals;
import com.example.bi.exception.InvalidSortException;
import com.example.bi.service.analytics.SalesWhereBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Repository
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record TrendRowData(
            String month,
            BigDecimal revenue,
            BigDecimal grossProfit,
            long unitsSold,
            BigDecimal prevRevenue
    ) {}

    public record BreakdownRowData(
            String name,
            BigDecimal revenue,
            BigDecimal grossProfit,
            long unitsSold,
            BigDecimal prevRevenue
    ) {}

    public record TopProductRowData(
            String product,
            BigDecimal revenue,
            BigDecimal grossProfit,
            long unitsSold,
            int revenueRank,
            int profitRank
    ) {}

    public record FilterOptionsData(
            List<String> categories,
            boolean categoriesTruncated,
            List<String> regions,
            boolean regionsTruncated,
            LocalDate minOrderDate,
            LocalDate maxOrderDate,
            int totalProducts
    ) {}

    public SalesTotals totals(
            UUID datasetId,
            LocalDate from,
            LocalDate toExclusive,
            List<String> categories,
            List<String> regions,
            String product
    ) {
        SalesWhereBuilder.WhereClause where = SalesWhereBuilder.build(
                datasetId, from, toExclusive, categories, regions, product
        );

        String sql = """
                SELECT
                    COALESCE(SUM(line_revenue), 0.00) AS revenue,
                    COALESCE(SUM(line_gross_profit), 0.00) AS gross_profit,
                    COALESCE(SUM(quantity), 0) AS units_sold,
                    COUNT(DISTINCT order_id) AS orders,
                    COUNT(*) AS line_items
                FROM sales_records
                """ + where.sql();

        return jdbcTemplate.queryForObject(sql, where.params(), (rs, rowNum) -> new SalesTotals(
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("gross_profit"),
                rs.getLong("units_sold"),
                rs.getLong("orders"),
                rs.getLong("line_items")
        ));
    }

    public List<TrendRowData> monthlyTrends(
            UUID datasetId,
            LocalDate from,
            LocalDate toInclusive,
            LocalDate toExclusive,
            List<String> categories,
            List<String> regions,
            String product
    ) {
        if (from == null || toInclusive == null) {
            return Collections.emptyList();
        }

        SalesWhereBuilder.WhereClause where = SalesWhereBuilder.build(
                datasetId, from, toExclusive, categories, regions, product, "filter_"
        );

        MapSqlParameterSource params = new MapSqlParameterSource(where.params().getValues());
        params.addValue("series_from", from);
        params.addValue("series_to", toInclusive);

        String sql = """
                WITH months AS (
                    SELECT generate_series(
                        date_trunc('month', CAST(:series_from AS date)),
                        date_trunc('month', CAST(:series_to AS date)),
                        '1 month'::interval
                    )::date AS month_start
                ),
                monthly_sales AS (
                    SELECT
                        date_trunc('month', order_date)::date AS month_start,
                        COALESCE(SUM(line_revenue), 0.00) AS revenue,
                        COALESCE(SUM(line_gross_profit), 0.00) AS gross_profit,
                        COALESCE(SUM(quantity), 0) AS units_sold
                    FROM sales_records
                """ + where.sql() + """
                    GROUP BY date_trunc('month', order_date)::date
                )
                SELECT
                    to_char(m.month_start, 'YYYY-MM') AS month_str,
                    COALESCE(s.revenue, 0.00) AS revenue,
                    COALESCE(s.gross_profit, 0.00) AS gross_profit,
                    COALESCE(s.units_sold, 0) AS units_sold,
                    LAG(s.revenue) OVER (ORDER BY m.month_start) AS prev_revenue
                FROM months m
                LEFT JOIN monthly_sales s ON m.month_start = s.month_start
                ORDER BY m.month_start ASC
                """;

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new TrendRowData(
                rs.getString("month_str"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("gross_profit"),
                rs.getLong("units_sold"),
                rs.getBigDecimal("prev_revenue")
        ));
    }

    public List<BreakdownRowData> categoryBreakdown(
            UUID datasetId,
            LocalDate from,
            LocalDate toExclusive,
            List<String> categories,
            List<String> regions,
            String product,
            String sortParam,
            String directionParam
    ) {
        SalesWhereBuilder.WhereClause where = SalesWhereBuilder.build(
                datasetId, from, toExclusive, categories, regions, product
        );

        String sortColumn = resolveCategorySortColumn(sortParam);
        String direction = resolveDirection(directionParam, sortParam);

        String sql = """
                SELECT
                    category AS name,
                    COALESCE(SUM(line_revenue), 0.00) AS revenue,
                    COALESCE(SUM(line_gross_profit), 0.00) AS gross_profit,
                    COALESCE(SUM(quantity), 0) AS units_sold
                FROM sales_records
                %s
                GROUP BY category
                ORDER BY %s %s, category ASC
                """.formatted(where.sql(), sortColumn, direction);

        return jdbcTemplate.query(sql, where.params(), (rs, rowNum) -> new BreakdownRowData(
                rs.getString("name"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("gross_profit"),
                rs.getLong("units_sold"),
                null
        ));
    }

    public List<BreakdownRowData> regionBreakdown(
            UUID datasetId,
            LocalDate from,
            LocalDate toExclusive,
            List<String> categories,
            List<String> regions,
            String product,
            String sortParam,
            String directionParam,
            LocalDate prevFrom,
            LocalDate prevToExclusive
    ) {
        String sortColumn = resolveRegionSortColumn(sortParam);
        String direction = resolveDirection(directionParam, sortParam);

        if (prevFrom != null && prevToExclusive != null) {
            SalesWhereBuilder.WhereClause currWhere = SalesWhereBuilder.build(
                    datasetId, from, toExclusive, categories, regions, product, "curr_"
            );
            SalesWhereBuilder.WhereClause prevWhere = SalesWhereBuilder.build(
                    datasetId, prevFrom, prevToExclusive, categories, regions, product, "prev_"
            );

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValues(currWhere.params().getValues());
            params.addValues(prevWhere.params().getValues());

            String sql = """
                    WITH current_agg AS (
                        SELECT
                            region,
                            COALESCE(SUM(line_revenue), 0.00) AS revenue,
                            COALESCE(SUM(line_gross_profit), 0.00) AS gross_profit,
                            COALESCE(SUM(quantity), 0) AS units_sold
                        FROM sales_records
                        %s
                        GROUP BY region
                    ),
                    prev_agg AS (
                        SELECT
                            region,
                            COALESCE(SUM(line_revenue), 0.00) AS prev_revenue
                        FROM sales_records
                        %s
                        GROUP BY region
                    )
                    SELECT
                        c.region AS name,
                        c.revenue,
                        c.gross_profit,
                        c.units_sold,
                        p.prev_revenue
                    FROM current_agg c
                    LEFT JOIN prev_agg p ON c.region = p.region
                    ORDER BY %s %s, c.region ASC
                    """.formatted(currWhere.sql(), prevWhere.sql(), sortColumn, direction);

            return jdbcTemplate.query(sql, params, (rs, rowNum) -> new BreakdownRowData(
                    rs.getString("name"),
                    rs.getBigDecimal("revenue"),
                    rs.getBigDecimal("gross_profit"),
                    rs.getLong("units_sold"),
                    rs.getBigDecimal("prev_revenue")
            ));
        } else {
            SalesWhereBuilder.WhereClause where = SalesWhereBuilder.build(
                    datasetId, from, toExclusive, categories, regions, product
            );

            String sql = """
                    SELECT
                        region AS name,
                        COALESCE(SUM(line_revenue), 0.00) AS revenue,
                        COALESCE(SUM(line_gross_profit), 0.00) AS gross_profit,
                        COALESCE(SUM(quantity), 0) AS units_sold,
                        NULL::numeric AS prev_revenue
                    FROM sales_records
                    %s
                    GROUP BY region
                    ORDER BY %s %s, region ASC
                    """.formatted(where.sql(), sortColumn, direction);

            return jdbcTemplate.query(sql, where.params(), (rs, rowNum) -> new BreakdownRowData(
                    rs.getString("name"),
                    rs.getBigDecimal("revenue"),
                    rs.getBigDecimal("gross_profit"),
                    rs.getLong("units_sold"),
                    null
            ));
        }
    }

    public List<TopProductRowData> topProducts(
            UUID datasetId,
            LocalDate from,
            LocalDate toExclusive,
            List<String> categories,
            List<String> regions,
            String product,
            int limit,
            RankBy rankBy
    ) {
        SalesWhereBuilder.WhereClause where = SalesWhereBuilder.build(
                datasetId, from, toExclusive, categories, regions, product
        );

        MapSqlParameterSource params = new MapSqlParameterSource(where.params().getValues());
        params.addValue("limit", limit);

        String orderClause = (rankBy == RankBy.PROFIT) ? "profit_rank ASC" : "revenue_rank ASC";

        String sql = """
                WITH product_agg AS (
                    SELECT
                        product_name,
                        COALESCE(SUM(line_revenue), 0.00) AS revenue,
                        COALESCE(SUM(line_gross_profit), 0.00) AS gross_profit,
                        COALESCE(SUM(quantity), 0) AS units_sold,
                        ROW_NUMBER() OVER (ORDER BY SUM(line_revenue) DESC, product_name ASC) AS revenue_rank,
                        ROW_NUMBER() OVER (ORDER BY SUM(line_gross_profit) DESC, product_name ASC) AS profit_rank
                    FROM sales_records
                    %s
                    GROUP BY product_name
                )
                SELECT product_name, revenue, gross_profit, units_sold, revenue_rank, profit_rank
                FROM product_agg
                ORDER BY %s
                LIMIT :limit
                """.formatted(where.sql(), orderClause);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new TopProductRowData(
                rs.getString("product_name"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("gross_profit"),
                rs.getLong("units_sold"),
                rs.getInt("revenue_rank"),
                rs.getInt("profit_rank")
        ));
    }

    public FilterOptionsData filterOptions(UUID datasetId) {
        MapSqlParameterSource params = new MapSqlParameterSource("datasetId", datasetId);

        // Fetch distinct categories up to 501
        String catSql = """
                SELECT DISTINCT category
                FROM sales_records
                WHERE dataset_id = :datasetId
                ORDER BY category ASC
                LIMIT 501
                """;
        List<String> allCats = jdbcTemplate.queryForList(catSql, params, String.class);
        boolean catsTruncated = allCats.size() > 500;
        List<String> categories = catsTruncated ? allCats.subList(0, 500) : allCats;

        // Fetch distinct regions up to 501
        String regSql = """
                SELECT DISTINCT region
                FROM sales_records
                WHERE dataset_id = :datasetId
                ORDER BY region ASC
                LIMIT 501
                """;
        List<String> allRegs = jdbcTemplate.queryForList(regSql, params, String.class);
        boolean regsTruncated = allRegs.size() > 500;
        List<String> regions = regsTruncated ? allRegs.subList(0, 500) : allRegs;

        // Fetch min/max dates and total distinct products
        String statsSql = """
                SELECT
                    MIN(order_date) AS min_date,
                    MAX(order_date) AS max_date,
                    COUNT(DISTINCT product_name) AS total_products
                FROM sales_records
                WHERE dataset_id = :datasetId
                """;
        return jdbcTemplate.queryForObject(statsSql, params, (rs, rowNum) -> {
            Date minD = rs.getDate("min_date");
            Date maxD = rs.getDate("max_date");
            LocalDate minDate = (minD != null) ? minD.toLocalDate() : null;
            LocalDate maxDate = (maxD != null) ? maxD.toLocalDate() : null;
            int totalProducts = rs.getInt("total_products");
            return new FilterOptionsData(
                    categories,
                    catsTruncated,
                    regions,
                    regsTruncated,
                    minDate,
                    maxDate,
                    totalProducts
            );
        });
    }

    private String resolveCategorySortColumn(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("revenue")) {
            return "revenue";
        }
        String normalized = sort.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "profit" -> "gross_profit";
            case "units" -> "units_sold";
            case "name" -> "category";
            default -> throw new InvalidSortException("Invalid category sort field: " + sort + ". Allowed: revenue, profit, units, name");
        };
    }

    private String resolveRegionSortColumn(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("revenue")) {
            return "revenue";
        }
        String normalized = sort.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "profit" -> "gross_profit";
            case "units" -> "units_sold";
            case "name" -> "name";
            case "margin" -> "(CASE WHEN revenue > 0 THEN (gross_profit / revenue) ELSE 0 END)";
            default -> throw new InvalidSortException("Invalid region sort field: " + sort + ". Allowed: revenue, profit, units, name, margin");
        };
    }

    private String resolveDirection(String direction, String sortField) {
        if (direction == null || direction.isBlank()) {
            return ("name".equalsIgnoreCase(sortField)) ? "ASC" : "DESC";
        }
        String normalized = direction.toUpperCase(Locale.ROOT);
        if ("ASC".equals(normalized) || "DESC".equals(normalized)) {
            return normalized;
        }
        throw new InvalidSortException("Invalid sort direction: " + direction + ". Allowed: asc, desc");
    }
}
