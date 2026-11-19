package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.DataSource;
import com.dsinnovators.ei.source.RawRecordStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Stores a project as a raw record and fills or updates its {@code project} row, in one transaction. */
@Component
class ProjectRecorder {

    private final RawRecordStore store;
    private final ProjectRepository projects;
    private final OpenProjectSource source;

    ProjectRecorder(RawRecordStore store, ProjectRepository projects, OpenProjectSource source) {
        this.store = store;
        this.projects = projects;
        this.source = source;
    }

    /**
     * Stores the project as OpenProject gave it and brings its row up to date.
     *
     * @throws OpenProjectApiException when the answer is not a project or has no id, identifier or name; nothing is stored
     */
    @Transactional
    StoredRecord store(JsonNode project) {
        if (!"Project".equals(project.path("_type").asString())) {
            throw new OpenProjectApiException(
                    "The answer for project " + project.path("id").asString("unknown") + " is not of the kind Project");
        }
        String externalId = text(project, "id");
        String identifier = text(project, "identifier");
        String name = text(project, "name");
        DataSource dataSource = source.dataSource();
        String url = dataSource.getBaseUrl() + "/projects/" + identifier;
        StoredRecord stored = new StoredRecord(
                OpenProjectRecordKind.PROJECT,
                store.store(OpenProjectSource.KEY, OpenProjectRecordKind.PROJECT.recordKind(), externalId, url, project)
                        .outcome());
        projects.upsert(OpenProjectSource.KEY, externalId, identifier, name, url);
        return stored;
    }

    private static String text(JsonNode project, String field) {
        JsonNode value = project.path(field);
        if (!value.isValueNode() || value.isNull() || value.asString().isBlank()) {
            throw new OpenProjectApiException(
                    "The answer for project " + project.path("id").asString("unknown") + " has no " + field);
        }
        return value.asString();
    }
}
