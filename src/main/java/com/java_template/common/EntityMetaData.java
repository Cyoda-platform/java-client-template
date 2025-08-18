package com.java_template.common;

import java.util.UUID;

public record EntityMetaData(
        UUID id,
        String state,
        String creationDate
) {}
