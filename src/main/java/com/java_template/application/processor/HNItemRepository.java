package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory repository emulation for HNItem used by processors during testing/demo.
 * Not intended for production use. Thread-safe minimal implementation.
 */
public class HNItemRepository {

    private static final HNItemRepository INSTANCE = new HNItemRepository();
    private final Map<String, HNItem> byTechnicalId = new ConcurrentHashMap<>();
    private final Map<Long, String> hnIdToTechnicalId = new ConcurrentHashMap<>();

    private HNItemRepository() {}

    public static HNItemRepository getInstance() {
        return INSTANCE;
    }

    public HNItem findByTechnicalId(String technicalId) {
        return byTechnicalId.get(technicalId);
    }

    public HNItem findByHnId(Long hnId) {
        String tid = hnIdToTechnicalId.get(hnId);
        if (tid == null) return null;
        return byTechnicalId.get(tid);
    }

    public void save(HNItem item) {
        if (item.getTechnicalId() != null) {
            byTechnicalId.put(item.getTechnicalId(), item);
            if (item.getHnId() != null) {
                hnIdToTechnicalId.put(item.getHnId(), item.getTechnicalId());
            }
        }
    }

    public Collection<HNItem> findAll() {
        return byTechnicalId.values();
    }
}
