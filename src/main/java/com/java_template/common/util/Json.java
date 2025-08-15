package com.java_template.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Simple static Json helper to provide a singleton ObjectMapper for code that expects
 * com.java_template.common.util.Json.mapper(). This mirrors a small, backwards-compatible
 * façade used by multiple processors in the prototype.
 */
public final class Json {
    private static final ObjectMapper MAPPER = createMapper();

    private Json() {}

    private static ObjectMapper createMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
