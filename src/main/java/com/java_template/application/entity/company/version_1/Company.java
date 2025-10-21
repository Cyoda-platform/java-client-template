package com.java_template.application.entity.company.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: This file contains the Company entity that represents companies
 * in the CRM system with contact information and business details.
 */
@Data
public class Company implements CyodaEntity {
    public static final String ENTITY_NAME = Company.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String companyId;
    
    // Required core business fields
    private String name;
    
    // Optional business fields
    private String industry;
    private String website;
    private String description;
    private CompanyContact contact;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return companyId != null && !companyId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty();
    }

    /**
     * Nested class for company contact information
     * Groups related contact fields together
     */
    @Data
    public static class CompanyContact {
        private String contactName;
        private String email;
        private String phone;
        private String mobile;
        private CompanyAddress address;
    }

    /**
     * Nested class for company address information
     * Represents the company's physical address
     */
    @Data
    public static class CompanyAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }
}
