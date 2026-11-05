package com.example.bi.exception;

public class ExportTooLargeException extends RuntimeException {

    private final long count;

    public ExportTooLargeException(long count) {
        super(String.format("Export exceeds the maximum limit of 100,000 rows (found %,d rows). Please narrow your filters before exporting.", count));
        this.count = count;
    }

    public long getCount() {
        return count;
    }
}
