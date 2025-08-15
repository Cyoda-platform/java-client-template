package com.java_template.application.store;

import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory, thread-safe store for Hacker News items used by processors.
 * This is an ephemeral store and intended for prototype/testing only.
 */
@Component
public class InMemoryHnItemStore {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryHnItemStore.class);
    private final Map<Integer, HackerNewsItem> store = new ConcurrentHashMap<>();

    public Optional<HackerNewsItem> get(Integer id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Atomically upsert an item. Returns true if created (no previous value), false if updated.
     */
    public boolean upsert(HackerNewsItem item) {
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException("Item and item.id must not be null");
        }
        Integer id = item.getId();
        HackerNewsItem previous = store.put(id, item);
        boolean created = previous == null;
        logger.info("Upserted HN item id={} created={}", id, created);
        return created;
    }
}
