package com.example.bi.service.ingestion;

import com.example.bi.domain.IssueSeverity;
import com.example.bi.domain.RowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RowValidatorTest {

    private Clock fixedClock;
    private RowValidator validator;

    @BeforeEach
    void setUp() {
        // Fixed at 2026-02-15
        fixedClock = Clock.fixed(Instant.parse("2026-02-15T12:00:00Z"), ZoneId.of("UTC"));
        validator = new RowValidator(fixedClock, "ISO", new RowNormalizer());
    }

    private Map<String, String> validRowMap() {
        Map<String, String> m = new HashMap<>();
        m.put("order_id", "A-1001");
        m.put("order_date", "2026-01-15");
        m.put("product_name", "Wireless Mouse");
        m.put("category", "Electronics");
        m.put("region", "North");
        m.put("quantity", "10");
        m.put("unit_price", "500.00");
        m.put("unit_cost", "300.00");
        return m;
    }

    @Test
    void validRow_parsedSuccessfully() {
        Map<String, String> row = validRowMap();
        ParsedRow parsed = validator.validateRow(2, row);

        assertEquals(RowStatus.VALID, parsed.status());
        assertEquals("A-1001", parsed.orderId());
        assertEquals(LocalDate.of(2026, 1, 15), parsed.orderDate());
        assertEquals("Wireless Mouse", parsed.productName());
        assertEquals("Electronics", parsed.category());
        assertEquals("North", parsed.region());
        assertEquals(10, parsed.quantity());
        assertEquals(new BigDecimal("500.00"), parsed.unitPrice());
        assertEquals(new BigDecimal("300.00"), parsed.unitCost());
        assertNotNull(parsed.rowHash());
        assertEquals(64, parsed.rowHash().length());
        assertTrue(parsed.issues().isEmpty());
    }

    @Test
    void missingRequiredField_marksInvalid() {
        Map<String, String> row = validRowMap();
        row.remove("product_name");

        ParsedRow parsed = validator.validateRow(3, row);
        assertEquals(RowStatus.INVALID, parsed.status());
        assertNull(parsed.rowHash());
        assertEquals(1, parsed.issues().size());
        assertEquals("REQUIRED_VALUE_MISSING", parsed.issues().get(0).code());
        assertEquals("product_name", parsed.issues().get(0).columnName());
        assertNotNull(parsed.rawValues());
    }

    @Test
    void quantityValidation_rules() {
        // 10.0 accepted as whole integer 10
        Map<String, String> r1 = validRowMap();
        r1.put("quantity", "10.0");
        assertEquals(10, validator.validateRow(2, r1).quantity());

        // 2.5 rejected as INVALID_INTEGER
        Map<String, String> r2 = validRowMap();
        r2.put("quantity", "2.5");
        ParsedRow p2 = validator.validateRow(2, r2);
        assertEquals(RowStatus.INVALID, p2.status());
        assertEquals("INVALID_INTEGER", p2.issues().get(0).code());

        // 0 rejected as QUANTITY_NOT_POSITIVE
        Map<String, String> r3 = validRowMap();
        r3.put("quantity", "0");
        ParsedRow p3 = validator.validateRow(2, r3);
        assertEquals(RowStatus.INVALID, p3.status());
        assertEquals("QUANTITY_NOT_POSITIVE", p3.issues().get(0).code());

        // > 1,000,000 rejected as QUANTITY_TOO_LARGE
        Map<String, String> r4 = validRowMap();
        r4.put("quantity", "1000001");
        ParsedRow p4 = validator.validateRow(2, r4);
        assertEquals(RowStatus.INVALID, p4.status());
        assertEquals("QUANTITY_TOO_LARGE", p4.issues().get(0).code());
    }

    @Test
    void currencyAndThousandsFormatting_handled() {
        Map<String, String> row = validRowMap();
        row.put("unit_price", "₹1,200.50");
        row.put("unit_cost", "$800.00");

        ParsedRow parsed = validator.validateRow(2, row);
        assertEquals(RowStatus.VALID, parsed.status());
        assertEquals(new BigDecimal("1200.50"), parsed.unitPrice());
        assertEquals(new BigDecimal("800.00"), parsed.unitCost());
    }

    @Test
    void dateValidation_rules() {
        // Before 2000-01-01 -> error DATE_OUT_OF_RANGE
        Map<String, String> r1 = validRowMap();
        r1.put("order_date", "1999-12-31");
        ParsedRow p1 = validator.validateRow(2, r1);
        assertEquals(RowStatus.INVALID, p1.status());
        assertEquals("DATE_OUT_OF_RANGE", p1.issues().get(0).code());

        // > 366 days in future -> error DATE_OUT_OF_RANGE
        Map<String, String> r2 = validRowMap();
        r2.put("order_date", "2027-03-01"); // > 366 days after 2026-02-15
        ParsedRow p2 = validator.validateRow(2, r2);
        assertEquals(RowStatus.INVALID, p2.status());
        assertEquals("DATE_OUT_OF_RANGE", p2.issues().get(0).code());

        // Future date <= 366 days -> valid with warning FUTURE_DATE
        Map<String, String> r3 = validRowMap();
        r3.put("order_date", "2026-03-01");
        ParsedRow p3 = validator.validateRow(2, r3);
        assertEquals(RowStatus.VALID, p3.status());
        assertEquals(1, p3.issues().size());
        assertEquals(IssueSeverity.WARNING, p3.issues().get(0).severity());
        assertEquals("FUTURE_DATE", p3.issues().get(0).code());
    }

    @Test
    void warningRules_costExceedsPriceAndZeroPrice() {
        Map<String, String> row = validRowMap();
        row.put("unit_price", "100.00");
        row.put("unit_cost", "150.00");

        ParsedRow parsed = validator.validateRow(2, row);
        assertEquals(RowStatus.VALID, parsed.status());
        assertEquals(1, parsed.issues().size());
        assertEquals("COST_EXCEEDS_PRICE", parsed.issues().get(0).code());
        assertEquals(IssueSeverity.WARNING, parsed.issues().get(0).severity());

        Map<String, String> rowZero = validRowMap();
        rowZero.put("unit_price", "0.00");
        ParsedRow pZero = validator.validateRow(2, rowZero);
        assertEquals(RowStatus.VALID, pZero.status());
        assertTrue(pZero.issues().stream().anyMatch(i -> "ZERO_PRICE".equals(i.code())));
    }

    @Test
    void hashDeterminism_sameInputsProduceSameHash() {
        String h1 = RowNormalizer.computeRowHash("O-1", LocalDate.of(2026, 1, 1), "Prod", "Cat", "Reg", 5, new BigDecimal("10.00"), new BigDecimal("5.00"));
        String h2 = RowNormalizer.computeRowHash("O-1", LocalDate.of(2026, 1, 1), "Prod", "Cat", "Reg", 5, new BigDecimal("10.000"), new BigDecimal("5.00"));
        assertEquals(h1, h2);
    }
}

