package com.java_template.application.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.entity.importevent.version_1.ImportEvent;
import com.java_template.application.entity.importjob.version_1.ImportJob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory store used by processors/criteria in this prototype.
 * Provides minimal persistence semantics for duplicate detection and audit events.
 */
public class InMemoryDataStore {

    // Map HN id -> originalJson (JsonNode)
    public static final Map<Long, JsonNode> itemsByHnId = new ConcurrentHashMap<>();

    // Map technicalId -> HackerNewsItem metadata
    public static final Map<String, HackerNewsItem> itemsByTechnicalId = new ConcurrentHashMap<>();

    // Map jobTechnicalId -> ImportJob
    public static final Map<String, ImportJob> jobsByTechnicalId = new ConcurrentHashMap<>();

    // All import events
    public static final List<ImportEvent> importEvents = Collections.synchronizedList(new ArrayList<>());

    public static void clear() {
        itemsByHnId.clear();
        itemsByTechnicalId.clear();
        jobsByTechnicalId.clear();
        importEvents.clear();
    }

    private InMemoryDataStore() {}
}
