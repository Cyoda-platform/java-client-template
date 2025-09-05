package com.java_template.common.serializer;

import lombok.Getter;

/**
 * ABOUTME: Enumeration defining available serializer types for workflow
 * component serialization and deserialization operations.
 */
@Getter
public enum SerializerEnum {
    JACKSON("jackson");

    private final String type;

    SerializerEnum(String type) {
        this.type = type;
    }

}
