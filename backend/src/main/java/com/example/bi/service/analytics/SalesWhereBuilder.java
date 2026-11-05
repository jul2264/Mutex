package com.example.bi.service.analytics;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SalesWhereBuilder {

    private SalesWhereBuilder() {}

    public record WhereClause(String sql, MapSqlParameterSource params) {}

    public static WhereClause build(
            UUID datasetId,
            LocalDate from,
            LocalDate toExclusive,
            List<String> categories,
            List<String> regions,
            String product
    ) {
        return build(datasetId, from, toExclusive, categories, regions, product, "");
    }

    public static WhereClause build(
            UUID datasetId,
            LocalDate from,
            LocalDate toExclusive,
            List<String> categories,
            List<String> regions,
            String product,
            String paramPrefix
    ) {
        String pfx = (paramPrefix != null) ? paramPrefix : "";
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sb = new StringBuilder(" WHERE dataset_id = :" + pfx + "datasetId");
        params.addValue(pfx + "datasetId", datasetId);

        if (from != null) {
            sb.append(" AND order_date >= :").append(pfx).append("from");
            params.addValue(pfx + "from", from);
        }
        if (toExclusive != null) {
            sb.append(" AND order_date < :").append(pfx).append("toExclusive");
            params.addValue(pfx + "toExclusive", toExclusive);
        }
        if (categories != null && !categories.isEmpty()) {
            sb.append(" AND category IN (:").append(pfx).append("categories)");
            params.addValue(pfx + "categories", categories);
        }
        if (regions != null && !regions.isEmpty()) {
            sb.append(" AND region IN (:").append(pfx).append("regions)");
            params.addValue(pfx + "regions", regions);
        }
        if (product != null && !product.isBlank()) {
            sb.append(" AND product_name ILIKE :").append(pfx).append("productPattern ESCAPE '\\'");
            String escaped = escapeIlike(product.trim());
            params.addValue(pfx + "productPattern", "%" + escaped + "%");
        }

        return new WhereClause(sb.toString() + " ", params);
    }

    public static String escapeIlike(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

