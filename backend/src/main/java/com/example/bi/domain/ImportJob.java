package com.example.bi.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 5)
    private FileFormat fileFormat;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "date_format", nullable = false, length = 20)
    private String dateFormat = "ISO";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportPolicy policy = ImportPolicy.IMPORT_VALID_ONLY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ImportJobStatus status;

    @Column(name = "file_error_code", length = 50)
    private String fileErrorCode;

    @Column(name = "file_error_message", length = 500)
    private String fileErrorMessage;

    @Column(name = "total_rows", nullable = false)
    private int totalRows = 0;

    @Column(name = "valid_rows", nullable = false)
    private int validRows = 0;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows = 0;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows = 0;

    @Column(name = "blank_rows_skipped", nullable = false)
    private int blankRowsSkipped = 0;

    @Column(name = "warning_count", nullable = false)
    private int warningCount = 0;

    @Column(name = "rows_processed", nullable = false)
    private int rowsProcessed = 0;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows = 0;

    @Column(name = "issues_truncated", nullable = false)
    private boolean issuesTruncated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public ImportJob() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public FileFormat getFileFormat() { return fileFormat; }
    public void setFileFormat(FileFormat fileFormat) { this.fileFormat = fileFormat; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }

    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }

    public ImportPolicy getPolicy() { return policy; }
    public void setPolicy(ImportPolicy policy) { this.policy = policy; }

    public ImportJobStatus getStatus() { return status; }
    public void setStatus(ImportJobStatus status) { this.status = status; }

    public String getFileErrorCode() { return fileErrorCode; }
    public void setFileErrorCode(String fileErrorCode) { this.fileErrorCode = fileErrorCode; }

    public String getFileErrorMessage() { return fileErrorMessage; }
    public void setFileErrorMessage(String fileErrorMessage) { this.fileErrorMessage = fileErrorMessage; }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getValidRows() { return validRows; }
    public void setValidRows(int validRows) { this.validRows = validRows; }

    public int getInvalidRows() { return invalidRows; }
    public void setInvalidRows(int invalidRows) { this.invalidRows = invalidRows; }

    public int getDuplicateRows() { return duplicateRows; }
    public void setDuplicateRows(int duplicateRows) { this.duplicateRows = duplicateRows; }

    public int getBlankRowsSkipped() { return blankRowsSkipped; }
    public void setBlankRowsSkipped(int blankRowsSkipped) { this.blankRowsSkipped = blankRowsSkipped; }

    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }

    public int getRowsProcessed() { return rowsProcessed; }
    public void setRowsProcessed(int rowsProcessed) { this.rowsProcessed = rowsProcessed; }

    public int getImportedRows() { return importedRows; }
    public void setImportedRows(int importedRows) { this.importedRows = importedRows; }

    public boolean isIssuesTruncated() { return issuesTruncated; }
    public void setIssuesTruncated(boolean issuesTruncated) { this.issuesTruncated = issuesTruncated; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}

