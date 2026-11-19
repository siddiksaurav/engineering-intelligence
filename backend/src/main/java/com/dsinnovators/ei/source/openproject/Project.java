package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.DataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** An OpenProject project. Not a source copy: a changed name or identifier updates the row. */
@Entity
@Table(name = "project")
class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_source_id")
    private DataSource dataSource;

    private String externalId;

    private String identifier;

    private String name;

    private String url;

    protected Project() {}

    Project(DataSource dataSource, String externalId, String identifier, String name, String url) {
        this.dataSource = dataSource;
        this.externalId = externalId;
        this.identifier = identifier;
        this.name = name;
        this.url = url;
    }

    DataSource getDataSource() {
        return dataSource;
    }

    String getExternalId() {
        return externalId;
    }

    String getIdentifier() {
        return identifier;
    }

    String getName() {
        return name;
    }

    String getUrl() {
        return url;
    }

    void update(String identifier, String name, String url) {
        this.identifier = identifier;
        this.name = name;
        this.url = url;
    }
}
