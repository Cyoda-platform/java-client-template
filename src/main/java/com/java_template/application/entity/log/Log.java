package com.java_template.application.entity.log;

import lombok.Data;

@Data
public class Log {
    private String entityType;
    private String event;
    private long timestamp;
    private String petName;
}
