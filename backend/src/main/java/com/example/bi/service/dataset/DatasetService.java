package com.example.bi.service.dataset;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.ImportJob;
import com.example.bi.dto.*;
import com.example.bi.exception.DatasetNameMismatchException;
import com.example.bi.exception.DatasetNameTakenException;
import com.example.bi.exception.InvalidSortException;
import com.example.bi.repository.DatasetRepository;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.service.identity.CurrentUserProvider;
import com.example.bi.service.identity.DatasetAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "createdat", "rowcount", "status");

    private final DatasetRepository datasetRepository;
    private final ImportJobRepository importJobRepository;
    private final DatasetAccessService datasetAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public DatasetService(
            DatasetRepository datasetRepository,
            ImportJobRepository importJobRepository,
            DatasetAccessService datasetAccessService,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.datasetRepository = datasetRepository;
        this.importJobRepository = importJobRepository;
        this.datasetAccessService = datasetAccessService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<DatasetResponse> listDatasets(int page, int size, String sortParam, String directionParam) {
        UUID userId = currentUserProvider.getCurrentUserId();

        int pageSize = Math.clamp(size, 1, 100);
        int pageIndex = Math.max(page, 0);

        String sortField = (sortParam != null && !sortParam.isBlank()) ? sortParam : "createdAt";
        String directionStr = (directionParam != null && !directionParam.isBlank()) ? directionParam : "desc";

        String normalizedSort = sortField.toLowerCase(Locale.ROOT);
        if (!ALLOWED_SORT_FIELDS.contains(normalizedSort)) {
            throw new InvalidSortException("Invalid sort field: " + sortField + ". Allowed fields: name, createdAt, rowCount, status");
        }

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(directionStr);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSortException("Invalid sort direction: " + directionStr + ". Must be asc or desc");
        }

        String entityProperty = switch (normalizedSort) {
            case "name" -> "name";
            case "rowcount" -> "rowCount";
            case "status" -> "status";
            default -> "createdAt";
        };

        PageRequest pageRequest = PageRequest.of(pageIndex, pageSize, Sort.by(direction, entityProperty));
        Page<Dataset> datasetPage = datasetRepository.findAllByOwnerId(userId, pageRequest);

        List<DatasetResponse> content = datasetPage.getContent().stream()
                .map(this::toDatasetResponse)
                .toList();

        return new PageResponse<>(content, datasetPage.getNumber(), datasetPage.getSize(), datasetPage.getTotalElements(), datasetPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DatasetDetailResponse getDatasetDetails(UUID datasetId) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        List<ImportJob> jobs = importJobRepository.findByDatasetIdAndOwnerIdOrderByCreatedAtDesc(datasetId, userId);
        List<ImportJobSummaryDto> history = jobs.stream()
                .map(this::toImportJobSummary)
                .toList();

        ImportJobSummaryDto latestImport = history.isEmpty() ? null : history.getFirst();

        return new DatasetDetailResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getCurrencyCode(),
                dataset.getStatus().name(),
                dataset.getRowCount(),
                dataset.getMinOrderDate(),
                dataset.getMaxOrderDate(),
                dataset.getDataVersion(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt(),
                latestImport,
                history
        );
    }

    @Transactional(readOnly = true)
    public List<ImportJobSummaryDto> getImportHistory(UUID datasetId) {
        datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        return importJobRepository.findByDatasetIdAndOwnerIdOrderByCreatedAtDesc(datasetId, userId).stream()
                .map(this::toImportJobSummary)
                .toList();
    }

    @Transactional
    public DatasetResponse renameDataset(UUID datasetId, RenameDatasetRequest request) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        String trimmedName = request.name().trim();
        if (datasetRepository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(userId, trimmedName, datasetId)) {
            throw new DatasetNameTakenException("Dataset name already taken: " + trimmedName);
        }

        dataset.setName(trimmedName);
        dataset.setUpdatedAt(Instant.now(clock));
        Dataset saved = datasetRepository.save(dataset);

        log.info("Renamed dataset {} to '{}' for user {}", datasetId, trimmedName, userId);
        return toDatasetResponse(saved);
    }

    @Transactional
    public void deleteDataset(UUID datasetId, String confirmName) {
        Dataset dataset = datasetAccessService.requireOwned(datasetId);
        UUID userId = currentUserProvider.getCurrentUserId();

        if (confirmName == null || !confirmName.equals(dataset.getName())) {
            throw new DatasetNameMismatchException("Confirmation name '" + confirmName + "' does not match dataset name '" + dataset.getName() + "'");
        }

        datasetRepository.delete(dataset);
        auditLog.info("AUDIT: User {} deleted dataset {}", userId, datasetId);
        log.info("Dataset {} deleted by user {}", datasetId, userId);
    }

    private DatasetResponse toDatasetResponse(Dataset dataset) {
        return new DatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getCurrencyCode(),
                dataset.getStatus().name(),
                dataset.getRowCount(),
                dataset.getMinOrderDate(),
                dataset.getMaxOrderDate(),
                dataset.getDataVersion(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt()
        );
    }

    private ImportJobSummaryDto toImportJobSummary(ImportJob job) {
        return new ImportJobSummaryDto(
                job.getId(),
                job.getDataset().getId(),
                job.getStatus().name(),
                job.getOriginalFilename(),
                job.getPolicy().name(),
                job.getTotalRows(),
                job.getValidRows(),
                job.getInvalidRows(),
                job.getDuplicateRows(),
                job.getImportedRows(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }
}

