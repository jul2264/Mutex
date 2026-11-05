package com.example.bi.web;

import com.example.bi.domain.CompareMode;
import com.example.bi.domain.RankBy;
import com.example.bi.dto.*;
import com.example.bi.service.analytics.AnalyticsFilter;
import com.example.bi.service.analytics.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasets/{datasetId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getSummary(
            @PathVariable UUID datasetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) CompareMode compare
    ) {
        AnalyticsFilter filter = new AnalyticsFilter(from, to, category, region, product, compare);
        SummaryResponse response = analyticsService.getSummary(datasetId, filter);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/trends")
    public ResponseEntity<TrendsResponse> getTrends(
            @PathVariable UUID datasetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) CompareMode compare
    ) {
        AnalyticsFilter filter = new AnalyticsFilter(from, to, category, region, product, compare);
        TrendsResponse response = analyticsService.getTrends(datasetId, filter);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<CategoriesResponse> getCategories(
            @PathVariable UUID datasetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) CompareMode compare,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        AnalyticsFilter filter = new AnalyticsFilter(from, to, category, region, product, compare);
        CategoriesResponse response = analyticsService.getCategories(datasetId, filter, sort, direction);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/regions")
    public ResponseEntity<RegionsResponse> getRegions(
            @PathVariable UUID datasetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) CompareMode compare,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        AnalyticsFilter filter = new AnalyticsFilter(from, to, category, region, product, compare);
        RegionsResponse response = analyticsService.getRegions(datasetId, filter, sort, direction);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/top-products")
    public ResponseEntity<TopProductsResponse> getTopProducts(
            @PathVariable UUID datasetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) CompareMode compare,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "REVENUE") RankBy rankBy
    ) {
        AnalyticsFilter filter = new AnalyticsFilter(from, to, category, region, product, compare);
        TopProductsResponse response = analyticsService.getTopProducts(datasetId, filter, limit, rankBy);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/filter-options")
    public ResponseEntity<FilterOptionsResponse> getFilterOptions(@PathVariable UUID datasetId) {
        FilterOptionsResponse response = analyticsService.getFilterOptions(datasetId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
