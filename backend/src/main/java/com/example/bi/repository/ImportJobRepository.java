package com.example.bi.repository;

import com.example.bi.domain.ImportJob;
import com.example.bi.domain.ImportJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    @Query("SELECT j FROM ImportJob j WHERE j.id = :id AND j.dataset.owner.id = :ownerId")
    Optional<ImportJob> findByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Query("SELECT j FROM ImportJob j WHERE j.id = :id AND j.dataset.id = :datasetId AND j.dataset.owner.id = :ownerId")
    Optional<ImportJob> findByIdAndDatasetIdAndOwnerId(@Param("id") UUID id, @Param("datasetId") UUID datasetId, @Param("ownerId") UUID ownerId);

    @Query("SELECT COUNT(j) > 0 FROM ImportJob j WHERE j.id = :id AND j.dataset.id = :datasetId AND j.dataset.owner.id = :ownerId")
    boolean existsByIdAndDatasetIdAndOwnerId(@Param("id") UUID id, @Param("datasetId") UUID datasetId, @Param("ownerId") UUID ownerId);

    List<ImportJob> findByDatasetIdAndStatusIn(UUID datasetId, Collection<ImportJobStatus> statuses);

    long countByCreatedByIdAndStatusIn(UUID userId, Collection<ImportJobStatus> statuses);

    long countByCreatedByIdAndCreatedAtAfter(UUID userId, Instant after);

    List<ImportJob> findByStatus(ImportJobStatus status);
}
