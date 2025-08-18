package com.java_template.common.serializer;

import lombok.Getter;

@Getter
public enum SerializerEnum {
    JACKSON("jackson");

    private final String type;

    SerializerEnum(String type) {
        this.type = type;
    }

    // Explicit getter to ensure availability even if Lombok isn't processed
    public String getType() {
        return this.type;
    }

}
