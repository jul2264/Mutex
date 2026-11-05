package com.example.bi.service.ingestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RowNormalizer {

    private final ConcurrentMap<String, String> canonicalNames = new ConcurrentHashMap<>();

    public String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        String collapsed = trimmed.replaceAll("\\s+", " ");
        String nfc = Normalizer.normalize(collapsed, Normalizer.Form.NFC);
        return canonicalNames.computeIfAbsent(nfc.toLowerCase(Locale.ROOT), k -> nfc);
    }

    public static String cleanNumericText(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return "";
        }
        // Tolerate one leading currency symbol: ₹, $, €, £
        if (s.startsWith("₹") || s.startsWith("$") || s.startsWith("€") || s.startsWith("£")) {
            s = s.substring(1).stripLeading();
        }
        // Remove standard thousands grouping comma
        s = s.replace(",", "");
        return s;
    }

    public static String computeRowHash(
            String orderId,
            LocalDate orderDate,
            String productName,
            String category,
            String region,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal unitCost
    ) {
        String priceStr = unitPrice != null ? unitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString() : "";
        String costStr = unitCost != null ? unitCost.setScale(2, RoundingMode.HALF_UP).toPlainString() : "";
        String dateStr = orderDate != null ? orderDate.toString() : "";
        String qtyStr = quantity != null ? quantity.toString() : "";

        String joined = String.join("\u001f",
                orderId != null ? orderId : "",
                dateStr,
                productName != null ? productName : "",
                category != null ? category : "",
                region != null ? region : "",
                qtyStr,
                priceStr,
                costStr
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
