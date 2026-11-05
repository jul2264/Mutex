package com.example.bi.service.ingestion;

import java.io.Closeable;
import java.util.List;

public interface RowReader extends Closeable {
    boolean hasNext();
    RawRow nextRow();
    List<String> getHeaderNames();
    List<String> getExtraColumns();
}

