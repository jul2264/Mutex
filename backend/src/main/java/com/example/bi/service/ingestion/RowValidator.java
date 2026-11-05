package com.example.bi.service.ingestion;

import com.example.bi.domain.IssueSeverity;
import com.example.bi.domain.RowStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

public class RowValidator {

    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");
    private static final BigDecimal MIN_MONEY = BigDecimal.ZERO;

    private final Clock clock;
    private final DateTimeFormatter dateFormatter;
    private final RowNormalizer normalizer;

    public RowValidator(Clock clock, String dateFormatPattern, RowNormalizer normalizer) {
        this.clock = clock;
        this.normalizer = normalizer != null ? normalizer : new RowNormalizer();
        this.dateFormatter = resolveFormatter(dateFormatPattern);
    }

    private static DateTimeFormatter resolveFormatter(String pattern) {
        if (pattern == null || pattern.equalsIgnoreCase("ISO") || pattern.equalsIgnoreCase("yyyy-MM-dd")) {
            return DateTimeFormatter.ISO_LOCAL_DATE;
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

    public ParsedRow validateRow(int rowNumber, Map<String, String> cells) {
        List<RowIssue> issues = new ArrayList<>();
        boolean hasError = false;

        // 1. order_id (max 64)
        String rawOrderId = cells.get("order_id");
        String orderId = null;
        if (isBlank(rawOrderId)) {
            issues.add(new RowIssue(rowNumber, "order_id", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "order_id is required", rawOrderId));
            hasError = true;
        } else {
            orderId = rawOrderId.strip();
            // In case of numeric formatted string from spreadsheets (e.g. "123.0")
            if (orderId.matches("^\\d+\\.0+$")) {
                orderId = orderId.substring(0, orderId.indexOf('.'));
            }
            if (orderId.length() > 64) {
                issues.add(new RowIssue(rowNumber, "order_id", IssueSeverity.ERROR, "VALUE_TOO_LONG", "order_id exceeds maximum length of 64", rawOrderId));
                hasError = true;
            }
        }

        // 2. order_date
        String rawOrderDate = cells.get("order_date");
        LocalDate orderDate = null;
        if (isBlank(rawOrderDate)) {
            issues.add(new RowIssue(rowNumber, "order_date", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "order_date is required", rawOrderDate));
            hasError = true;
        } else {
            String trimmedDate = rawOrderDate.strip();
            try {
                orderDate = LocalDate.parse(trimmedDate, dateFormatter);
                LocalDate today = LocalDate.now(clock);
                LocalDate maxFutureDate = today.plusDays(366);

                if (orderDate.isBefore(MIN_DATE)) {
                    issues.add(new RowIssue(rowNumber, "order_date", IssueSeverity.ERROR, "DATE_OUT_OF_RANGE", "order_date cannot be earlier than 2000-01-01", rawOrderDate));
                    hasError = true;
                } else if (orderDate.isAfter(maxFutureDate)) {
                    issues.add(new RowIssue(rowNumber, "order_date", IssueSeverity.ERROR, "DATE_OUT_OF_RANGE", "order_date cannot be more than 366 days after upload date", rawOrderDate));
                    hasError = true;
                } else if (orderDate.isAfter(today)) {
                    issues.add(new RowIssue(rowNumber, "order_date", IssueSeverity.WARNING, "FUTURE_DATE", "order_date is in the future", rawOrderDate));
                }
            } catch (DateTimeParseException e) {
                issues.add(new RowIssue(rowNumber, "order_date", IssueSeverity.ERROR, "INVALID_DATE", "order_date is not in valid format", rawOrderDate));
                hasError = true;
            }
        }

        // 3. product_name (max 255)
        String rawProductName = cells.get("product_name");
        String productName = null;
        if (isBlank(rawProductName)) {
            issues.add(new RowIssue(rowNumber, "product_name", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "product_name is required", rawProductName));
            hasError = true;
        } else {
            productName = normalizer.normalizeText(rawProductName);
            if (productName.length() > 255) {
                issues.add(new RowIssue(rowNumber, "product_name", IssueSeverity.ERROR, "VALUE_TOO_LONG", "product_name exceeds maximum length of 255", rawProductName));
                hasError = true;
            }
        }

        // 4. category (max 100)
        String rawCategory = cells.get("category");
        String category = null;
        if (isBlank(rawCategory)) {
            issues.add(new RowIssue(rowNumber, "category", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "category is required", rawCategory));
            hasError = true;
        } else {
            category = normalizer.normalizeText(rawCategory);
            if (category.length() > 100) {
                issues.add(new RowIssue(rowNumber, "category", IssueSeverity.ERROR, "VALUE_TOO_LONG", "category exceeds maximum length of 100", rawCategory));
                hasError = true;
            }
        }

        // 5. region (max 100)
        String rawRegion = cells.get("region");
        String region = null;
        if (isBlank(rawRegion)) {
            issues.add(new RowIssue(rowNumber, "region", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "region is required", rawRegion));
            hasError = true;
        } else {
            region = normalizer.normalizeText(rawRegion);
            if (region.length() > 100) {
                issues.add(new RowIssue(rowNumber, "region", IssueSeverity.ERROR, "VALUE_TOO_LONG", "region exceeds maximum length of 100", rawRegion));
                hasError = true;
            }
        }

        // 6. quantity (1 to 1,000,000 whole number)
        String rawQuantity = cells.get("quantity");
        Integer quantity = null;
        if (isBlank(rawQuantity)) {
            issues.add(new RowIssue(rowNumber, "quantity", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "quantity is required", rawQuantity));
            hasError = true;
        } else {
            String cleanQty = RowNormalizer.cleanNumericText(rawQuantity);
            try {
                // If contains decimal point, check if whole number (e.g. 10.0 is acceptable, 2.5 is not)
                if (cleanQty.contains(".")) {
                    BigDecimal bd = new BigDecimal(cleanQty);
                    if (bd.stripTrailingZeros().scale() > 0) {
                        issues.add(new RowIssue(rowNumber, "quantity", IssueSeverity.ERROR, "INVALID_INTEGER", "quantity must be a whole integer", rawQuantity));
                        hasError = true;
                    } else {
                        quantity = bd.intValueExact();
                    }
                } else {
                    quantity = Integer.parseInt(cleanQty);
                }

                if (quantity != null) {
                    if (quantity <= 0) {
                        issues.add(new RowIssue(rowNumber, "quantity", IssueSeverity.ERROR, "QUANTITY_NOT_POSITIVE", "quantity must be positive (greater than 0)", rawQuantity));
                        hasError = true;
                    } else if (quantity > 1_000_000) {
                        issues.add(new RowIssue(rowNumber, "quantity", IssueSeverity.ERROR, "QUANTITY_TOO_LARGE", "quantity exceeds maximum limit of 1,000,000", rawQuantity));
                        hasError = true;
                    }
                }
            } catch (NumberFormatException | ArithmeticException e) {
                issues.add(new RowIssue(rowNumber, "quantity", IssueSeverity.ERROR, "INVALID_INTEGER", "quantity must be a valid integer", rawQuantity));
                hasError = true;
            }
        }

        // 7. unit_price
        String rawUnitPrice = cells.get("unit_price");
        BigDecimal unitPrice = null;
        if (isBlank(rawUnitPrice)) {
            issues.add(new RowIssue(rowNumber, "unit_price", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "unit_price is required", rawUnitPrice));
            hasError = true;
        } else {
            String cleanPrice = RowNormalizer.cleanNumericText(rawUnitPrice);
            try {
                BigDecimal bd = new BigDecimal(cleanPrice);
                if (bd.compareTo(MIN_MONEY) < 0) {
                    issues.add(new RowIssue(rowNumber, "unit_price", IssueSeverity.ERROR, "NEGATIVE_VALUE", "unit_price cannot be negative", rawUnitPrice));
                    hasError = true;
                } else if (bd.compareTo(MAX_MONEY) > 0) {
                    issues.add(new RowIssue(rowNumber, "unit_price", IssueSeverity.ERROR, "VALUE_OUT_OF_RANGE", "unit_price exceeds maximum limit of 99,999,999.99", rawUnitPrice));
                    hasError = true;
                } else {
                    unitPrice = bd.setScale(2, RoundingMode.HALF_UP);
                    if (unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                        issues.add(new RowIssue(rowNumber, "unit_price", IssueSeverity.WARNING, "ZERO_PRICE", "unit_price is zero", rawUnitPrice));
                    }
                }
            } catch (NumberFormatException e) {
                issues.add(new RowIssue(rowNumber, "unit_price", IssueSeverity.ERROR, "INVALID_DECIMAL", "unit_price is not a valid decimal", rawUnitPrice));
                hasError = true;
            }
        }

        // 8. unit_cost
        String rawUnitCost = cells.get("unit_cost");
        BigDecimal unitCost = null;
        if (isBlank(rawUnitCost)) {
            issues.add(new RowIssue(rowNumber, "unit_cost", IssueSeverity.ERROR, "REQUIRED_VALUE_MISSING", "unit_cost is required", rawUnitCost));
            hasError = true;
        } else {
            String cleanCost = RowNormalizer.cleanNumericText(rawUnitCost);
            try {
                BigDecimal bd = new BigDecimal(cleanCost);
                if (bd.compareTo(MIN_MONEY) < 0) {
                    issues.add(new RowIssue(rowNumber, "unit_cost", IssueSeverity.ERROR, "NEGATIVE_VALUE", "unit_cost cannot be negative", rawUnitCost));
                    hasError = true;
                } else if (bd.compareTo(MAX_MONEY) > 0) {
                    issues.add(new RowIssue(rowNumber, "unit_cost", IssueSeverity.ERROR, "VALUE_OUT_OF_RANGE", "unit_cost exceeds maximum limit of 99,999,999.99", rawUnitCost));
                    hasError = true;
                } else {
                    unitCost = bd.setScale(2, RoundingMode.HALF_UP);
                }
            } catch (NumberFormatException e) {
                issues.add(new RowIssue(rowNumber, "unit_cost", IssueSeverity.ERROR, "INVALID_DECIMAL", "unit_cost is not a valid decimal", rawUnitCost));
                hasError = true;
            }
        }

        // Cross-field warnings (BR-17)
        if (unitPrice != null && unitCost != null && unitCost.compareTo(unitPrice) > 0) {
            issues.add(new RowIssue(rowNumber, "unit_cost", IssueSeverity.WARNING, "COST_EXCEEDS_PRICE", "unit_cost exceeds unit_price", rawUnitCost));
        }

        RowStatus status = hasError ? RowStatus.INVALID : RowStatus.VALID;
        String rowHash = null;
        if (status == RowStatus.VALID) {
            rowHash = RowNormalizer.computeRowHash(orderId, orderDate, productName, category, region, quantity, unitPrice, unitCost);
        }

        Map<String, String> rawMap = (status == RowStatus.INVALID) ? new HashMap<>(cells) : null;

        return new ParsedRow(
                rowNumber,
                status,
                orderId,
                orderDate,
                productName,
                category,
                region,
                quantity,
                unitPrice,
                unitCost,
                rowHash,
                rawMap,
                issues
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.strip().isEmpty();
    }
}

