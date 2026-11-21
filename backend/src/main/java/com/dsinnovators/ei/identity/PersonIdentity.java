package com.dsinnovators.ei.identity;

import com.dsinnovators.ei.source.DataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** How a person is known in one data source: a GitHub account or an OpenProject user. */
@Entity
@Table(name = "person_identity")
public class PersonIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "data_source_id")
    private DataSource dataSource;

    private String externalId;

    private String username;

    protected PersonIdentity() {}

    PersonIdentity(Person person, DataSource dataSource, String externalId, String username) {
        this.person = person;
        this.dataSource = dataSource;
        this.externalId = externalId;
        this.username = username;
    }

    DataSource getDataSource() {
        return dataSource;
    }

    String getExternalId() {
        return externalId;
    }

    Person getPerson() {
        return person;
    }

    String getUsername() {
        return username;
    }

    void rename(String username) {
        this.username = username;
    }
}
