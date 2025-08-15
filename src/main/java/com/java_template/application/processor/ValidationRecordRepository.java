package com.java_template.application.processor;

import com.java_template.application.entity.validationrecord.version_1.ValidationRecord;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ValidationRecordRepository {
    private static final ValidationRecordRepository INSTANCE = new ValidationRecordRepository();
    private final Map<String, ValidationRecord> byTechnicalId = new ConcurrentHashMap<>();

    private ValidationRecordRepository() {}

    public static ValidationRecordRepository getInstance() {
        return INSTANCE;
    }

    public void save(ValidationRecord record) {
        if (record.getTechnicalId() != null) {
            byTechnicalId.put(record.getTechnicalId(), record);
        }
    }

    public ValidationRecord findByTechnicalId(String technicalId) {
        return byTechnicalId.get(technicalId);
    }

    public Collection<ValidationRecord> findAll() {
        return byTechnicalId.values();
    }
}
