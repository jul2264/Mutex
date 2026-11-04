package com.example.bi.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "datasets")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DatasetStatus status = DatasetStatus.DRAFT;

    @Column(name = "row_count", nullable = false)
    private int rowCount = 0;

    @Column(name = "min_order_date")
    private LocalDate minOrderDate;

    @Column(name = "max_order_date")
    private LocalDate maxOrderDate;

    @Column(name = "data_version", nullable = false)
    private int dataVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Dataset() {}

    public Dataset(UUID id, User owner, String name, String currencyCode) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.currencyCode = currencyCode != null ? currencyCode : "INR";
        this.status = DatasetStatus.DRAFT;
        this.rowCount = 0;
        this.dataVersion = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public DatasetStatus getStatus() { return status; }
    public void setStatus(DatasetStatus status) { this.status = status; }

    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }

    public LocalDate getMinOrderDate() { return minOrderDate; }
    public void setMinOrderDate(LocalDate minOrderDate) { this.minOrderDate = minOrderDate; }

    public LocalDate getMaxOrderDate() { return maxOrderDate; }
    public void setMaxOrderDate(LocalDate maxOrderDate) { this.maxOrderDate = maxOrderDate; }

    public int getDataVersion() { return dataVersion; }
    public void setDataVersion(int dataVersion) { this.dataVersion = dataVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

