package com.java_template.common.serializer;

import lombok.Getter;

@Getter
public enum SerializerEnum {
    JACKSON("jackson");

    private final String type;

    SerializerEnum(String type) {
        this.type = type;
    }

}
