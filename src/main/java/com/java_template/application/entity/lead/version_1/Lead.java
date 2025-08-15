package com.java_template.application.entity.lead.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Lead implements CyodaEntity {
    public static final String ENTITY_NAME = "Lead";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String source; // e.g., webinar, referral
    private String status; // new, contacted, qualified
    private String company;
    private String interestedProduct;
    private String createdAt; // ISO timestamp

    public Lead() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Require at least an email or a phone to be present
        boolean hasContact = (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
        if (!hasContact) return false;
        return true;
    }
}
