package com.example.bi.service.ingestion;

import com.example.bi.domain.*;
import com.example.bi.dto.UploadResponse;
import com.example.bi.exception.DatasetNameTakenException;
import com.example.bi.exception.ImportInProgressException;
import com.example.bi.exception.UploadRateLimitException;
import com.example.bi.repository.DatasetRepository;
import com.example.bi.repository.ImportJobRepository;
import com.example.bi.repository.UserRepository;
import com.example.bi.service.identity.CurrentUserProvider;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.example.bi.exception.ServerBusyException;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class DatasetUploadService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final DatasetRepository datasetRepository;
    private final ImportJobRepository importJobRepository;
    private final UploadGuard uploadGuard;
    private final ImportWorker importWorker;

    public DatasetUploadService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            DatasetRepository datasetRepository,
            ImportJobRepository importJobRepository,
            UploadGuard uploadGuard,
            ImportWorker importWorker
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.datasetRepository = datasetRepository;
        this.importJobRepository = importJobRepository;
        this.uploadGuard = uploadGuard;
        this.importWorker = importWorker;
    }

    public UploadResponse uploadAndStartImport(
            MultipartFile file,
            String name,
            String dateFormat,
            ImportPolicy policy,
            String currency
    ) throws IOException {
        UUID userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        // Rate limiting: at most 10 uploads per hour per user
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        if (importJobRepository.countByCreatedByIdAndCreatedAtAfter(userId, oneHourAgo) >= 10) {
            throw new UploadRateLimitException("Upload rate limit exceeded. Maximum 10 uploads per hour.");
        }

        // Concurrency limiting: allow one PARSING job per user at a time
        if (importJobRepository.countByCreatedByIdAndStatusIn(userId, List.of(ImportJobStatus.PARSING)) > 0) {
            throw new ImportInProgressException("An import is already in progress for your account.");
        }

        String trimmedName = (name != null) ? name.strip() : "";
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Dataset name is required");
        }

        // Unique dataset name per user
        if (datasetRepository.existsByOwnerIdAndNameIgnoreCase(userId, trimmedName)) {
            throw new DatasetNameTakenException("A dataset with this name already exists.");
        }

        // Synchronous inspection and spooling to private temp file
        UploadGuard.GuardResult guardResult = uploadGuard.inspectAndSpool(file, trimmedName);

        ImportJob job;
        Dataset dataset;
        try {
            dataset = createDraftDataset(user, trimmedName, currency);
            job = createParsingJob(user, dataset, guardResult, dateFormat, policy);
        } catch (Exception ex) {
            if (guardResult.tempFile().exists()) {
                guardResult.tempFile().delete();
            }
            throw ex;
        }

        try {
            importWorker.processImportJob(job.getId(), guardResult.tempFile(), job.getDateFormat(), job.getFileFormat());
        } catch (TaskRejectedException tre) {
            // Executor queue is full
            if (guardResult.tempFile().exists()) {
                guardResult.tempFile().delete();
            }
            importJobRepository.delete(job);
            datasetRepository.delete(dataset);
            throw new ServerBusyException("Import executor queue is full. Please try again shortly.");
        }

        String statusUrl = "/api/datasets/" + dataset.getId() + "/imports/" + job.getId();
        return new UploadResponse(dataset.getId(), job.getId(), statusUrl);
    }

    @Transactional
    public Dataset createDraftDataset(User user, String name, String currency) {
        Dataset ds = new Dataset();
        ds.setOwner(user);
        ds.setName(name);
        ds.setCurrencyCode((currency != null && !currency.isBlank()) ? currency.strip().toUpperCase() : "INR");
        ds.setStatus(DatasetStatus.DRAFT);
        return datasetRepository.save(ds);
    }

    @Transactional
    public ImportJob createParsingJob(
            User user,
            Dataset dataset,
            UploadGuard.GuardResult guardResult,
            String dateFormat,
            ImportPolicy policy
    ) {
        ImportJob job = new ImportJob();
        job.setDataset(dataset);
        job.setCreatedBy(user);
        job.setOriginalFilename(guardResult.sanitizedFilename());
        job.setFileFormat(guardResult.format());
        job.setFileSizeBytes(guardResult.sizeBytes());
        job.setFileSha256(guardResult.sha256Hex());
        job.setDateFormat((dateFormat != null && !dateFormat.isBlank()) ? dateFormat.strip() : "ISO");
        job.setPolicy(policy != null ? policy : ImportPolicy.IMPORT_VALID_ONLY);
        job.setStatus(ImportJobStatus.PARSING);
        return importJobRepository.save(job);
    }
}
