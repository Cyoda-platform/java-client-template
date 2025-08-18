package com.java_template.common.repository;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.dto.Meta;
import com.java_template.common.util.HttpUtils;

import java.util.concurrent.CompletableFuture;

public class CyodaRepository implements CrudRepository {
    private static final String CYODA_API_URL = "http://localhost:8080/api";
    private final HttpUtils httpUtils;

    public CyodaRepository(HttpUtils httpUtils) {
        this.httpUtils = httpUtils;
    }

    @Override
    public CompletableFuture<ObjectNode> get(Meta meta) {
        String path = String.format("entity/%s/%s", meta.getEntityModel(), meta.getEntityVersion());
        return httpUtils.sendGetRequest(meta.getToken(), CYODA_API_URL, path);
    }

    @Override
    public CompletableFuture<ArrayNode> create(Meta meta, ObjectNode data) {
        String path = String.format("entity/%s/%s/%s", FORMAT, meta.getEntityModel(), meta.getEntityVersion());
        return httpUtils.sendPostRequest(meta.getToken(), CYODA_API_URL, path, data);
    }

    @Override
    public CompletableFuture<ObjectNode> update(Meta meta, String id, ObjectNode entity) {
        String path = String.format("entity/%s/%s/%s", FORMAT, id, meta.getUpdateTransition());
        return httpUtils.sendPutRequest(meta.getToken(), CYODA_API_URL, path, entity);
    }

    @Override
    public CompletableFuture<ObjectNode> delete(Meta meta) {
        String path = String.format("entity/%s/%s", meta.getEntityModel(), meta.getEntityVersion());
        return httpUtils.sendDeleteRequest(meta.getToken(), CYODA_API_URL, path).thenApply(response -> (ObjectNode) response.get("json"));
    }

    // Additional methods simplified for compilation
}
