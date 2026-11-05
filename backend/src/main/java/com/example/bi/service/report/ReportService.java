package com.example.bi.service.report;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.ExportFormat;
import com.example.bi.domain.ExportType;
import com.example.bi.domain.RankBy;
import com.example.bi.dto.*;
import com.example.bi.exception.ExportTooLargeException;
import com.example.bi.exception.InvalidFilterParameterException;
import com.example.bi.repository.AnalyticsRepository;
import com.example.bi.service.analytics.AnalyticsFilter;
import com.example.bi.service.analytics.AnalyticsFilterValidator;
import com.example.bi.service.analytics.AnalyticsService;
import com.example.bi.service.identity.CurrentUserProvider;
import com.example.bi.service.identity.DatasetAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final long MAX_EXPORT_ROWS = 100_000L;

    private final DatasetAccessService datasetAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final AnalyticsRepository analyticsRepository;
    private final AnalyticsService analyticsService;
    private final AnalyticsFilterValidator filterValidator;
    private final TransactionTemplate transactionTemplate;

    public ReportService(
            DatasetAccessService datasetAccessService,
            CurrentUserProvider currentUserProvider,
            AnalyticsRepository analyticsRepository,
            AnalyticsService analyticsService,
            AnalyticsFilterValidator filterValidator,
            PlatformTransactionManager transactionManager
    ) {
        this.datasetAccessService = datasetAccessService;
        this.currentUserProvider = currentUserProvider;
        this.analyticsRepository = analyticsRepository;
        this.analyticsService = analyticsService;
        this.filterValidator = filterValidator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    public record ExportResult(
            String filename,
            String contentType,
            StreamingResponseBody body
    ) {}

    public ExportResult generateExport(
            UUID datasetId,
            ExportFormat format,
            ExportType type,
            AnalyticsFilter filter
    ) {
        // 1. Access check (must return 404 if not owned)
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        // 2. Validate filter parameters
        filterValidator.validate(filter);

        // 3. Validation on format + type
        if (type == ExportType.SUMMARY && format == ExportFormat.CSV) {
            throw new InvalidFilterParameterException("Summary export is only supported in XLSX format.");
        }

        LocalDate toExclusive = filter.to() != null ? filter.to().plusDays(1) : null;
        String sanitizedDatasetName = dataset.getName().replaceAll("[^a-zA-Z0-9-_]", "_");
        String dateSuffix = LocalDate.now().toString();

        if (type == ExportType.RECORDS) {
            // 4. Pre-check row count
            long count = analyticsRepository.countRecords(
                    datasetId, filter.from(), toExclusive, filter.categories(), filter.regions(), filter.product()
            );

            if (count > MAX_EXPORT_ROWS) {
                throw new ExportTooLargeException(count);
            }

            String filename = String.format("%s_records_%s.%s", sanitizedDatasetName, dateSuffix, format.name().toLowerCase());
            String contentType = (format == ExportFormat.CSV)
                    ? "text/csv; charset=UTF-8"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

            StreamingResponseBody body = outputStream -> {
                transactionTemplate.execute(status -> {
                    try {
                        if (format == ExportFormat.CSV) {
                            CsvExportWriter writer = new CsvExportWriter(outputStream);
                            analyticsRepository.streamRecords(
                                    datasetId, filter.from(), toExclusive, filter.categories(), filter.regions(), filter.product(),
                                    rs -> {
                                        try {
                                            writer.writeRow(rs);
                                        } catch (Exception e) {
                                            throw new RuntimeException("Error streaming CSV row", e);
                                        }
                                    }
                            );
                            writer.flush();
                        } else {
                            try (XlsxExportWriter writer = new XlsxExportWriter()) {
                                writer.initRecordsSheet();
                                analyticsRepository.streamRecords(
                                        datasetId, filter.from(), toExclusive, filter.categories(), filter.regions(), filter.product(),
                                        rs -> {
                                            try {
                                                writer.writeRecordRow(rs);
                                            } catch (Exception e) {
                                                throw new RuntimeException("Error streaming XLSX row", e);
                                            }
                                        }
                                );
                                writer.writeTo(outputStream);
                            }
                        }
                        log.info("Export completed: userId={}, datasetId={}, format={}, type={}, rowCount={}",
                                userId, datasetId, format, type, count);
                        return null;
                    } catch (IOException e) {
                        log.warn("Export streaming interrupted: userId={}, datasetId={}, format={}", userId, datasetId, format);
                        throw new RuntimeException("Export streaming failed", e);
                    }
                });
            };

            return new ExportResult(filename, contentType, body);
        } else {
            // SUMMARY workbook (XLSX only)
            String filename = String.format("%s_summary_%s.xlsx", sanitizedDatasetName, dateSuffix);
            String contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

            SummaryResponse summary = analyticsService.getSummary(datasetId, filter);
            TrendsResponse trends = analyticsService.getTrends(datasetId, filter);
            CategoriesResponse categories = analyticsService.getCategories(datasetId, filter, "revenue", "desc");
            RegionsResponse regions = analyticsService.getRegions(datasetId, filter, "revenue", "desc");
            TopProductsResponse topProducts = analyticsService.getTopProducts(datasetId, filter, 50, RankBy.REVENUE);

            StreamingResponseBody body = outputStream -> {
                try (XlsxExportWriter writer = new XlsxExportWriter()) {
                    writer.writeSummaryWorkbook(dataset, filter, summary, trends, categories, regions, topProducts);
                    writer.writeTo(outputStream);
                    log.info("Export completed: userId={}, datasetId={}, format={}, type={}, rowCount={}",
                            userId, datasetId, format, type, summary.lineItems());
                } catch (IOException e) {
                    log.warn("Summary export streaming interrupted: userId={}, datasetId={}", userId, datasetId);
                    throw new RuntimeException("Summary export failed", e);
                }
            };

            return new ExportResult(filename, contentType, body);
        }
    }
}
