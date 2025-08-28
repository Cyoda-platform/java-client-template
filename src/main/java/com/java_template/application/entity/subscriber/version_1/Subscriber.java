package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String subscriberId; // technical id (serialized UUID or string)
    private String name;
    private Boolean active;
    private List<Channel> channels;
    private List<Filter> filters;
    private String lastNotifiedAt; // ISO-8601 timestamp as String

    public Subscriber() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // subscriberId and name must be present and non-blank
        if (subscriberId == null || subscriberId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        // active must be present
        if (active == null) return false;
        // channels must be present and each channel must have non-blank address and type
        if (channels == null || channels.isEmpty()) return false;
        for (Channel c : channels) {
            if (c == null) return false;
            if (c.getAddress() == null || c.getAddress().isBlank()) return false;
            if (c.getType() == null || c.getType().isBlank()) return false;
        }
        // filters are optional, but if present each filter's category must be non-blank
        if (filters != null) {
            for (Filter f : filters) {
                if (f == null) return false;
                if (f.getCategory() == null || f.getCategory().isBlank()) return false;
            }
        }
        // lastNotifiedAt is optional (can be null)
        return true;
    }

    @Data
    public static class Channel {
        private String address;
        private String type;
    }

    @Data
    public static class Filter {
        private String category;
    }
}