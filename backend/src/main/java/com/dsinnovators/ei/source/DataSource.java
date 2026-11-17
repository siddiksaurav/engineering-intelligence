package com.dsinnovators.ei.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A system records are pulled from: GitHub, OpenProject. The rows are seeded by a migration. */
@Entity
@Immutable
@Table(name = "data_source")
public class DataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "key" is a keyword in JPQL, so the attribute has another name than the column.
    @Column(name = "key")
    private String sourceKey;

    private String name;

    private String baseUrl;

    protected DataSource() {}

    public Long getId() {
        return id;
    }

    public String getKey() {
        return sourceKey;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
