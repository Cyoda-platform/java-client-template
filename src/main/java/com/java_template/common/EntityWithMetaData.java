package com.java_template.common;

public record EntityWithMetaData<ENTITY_TYPE>(
        EntityMetaData meta,
        ENTITY_TYPE entity
) {}
