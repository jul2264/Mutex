package com.example.bi.repository;

import com.example.bi.domain.Dataset;
import com.example.bi.domain.DatasetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
    Page<Dataset> findAllByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Dataset> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(UUID ownerId, String name, UUID id);

    List<Dataset> findByStatusAndCreatedAtBefore(DatasetStatus status, Instant cutoff);
}

