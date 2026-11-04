package com.example.bi.service.identity;

import com.example.bi.domain.Dataset;
import com.example.bi.exception.DatasetNotFoundException;
import com.example.bi.repository.DatasetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DatasetAccessService {

    private final DatasetRepository datasetRepository;
    private final CurrentUserProvider currentUserProvider;

    public DatasetAccessService(DatasetRepository datasetRepository, CurrentUserProvider currentUserProvider) {
        this.datasetRepository = datasetRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public Dataset requireOwned(UUID datasetId) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        return datasetRepository.findByIdAndOwnerId(datasetId, currentUserId)
                .orElseThrow(() -> new DatasetNotFoundException("Dataset not found: " + datasetId));
    }
}

