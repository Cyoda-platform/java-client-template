package com.java_template.application.processor;

import com.java_template.application.entity.DigestEmailRecord;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DigestEmailRecordProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DigestEmailRecordProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestEmailRecordProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestEmailRecord for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestEmailRecord.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(DigestEmailRecord entity) {
        return entity != null && entity.isValid();
    }

    private DigestEmailRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DigestEmailRecord> context) {
        DigestEmailRecord entity = context.entity();

        // Business logic for processDigestEmailRecord() flow from functional_requirement.md:
        // 1. Email Sending:
        //    - Dispatch email to the userEmail referenced by jobTechnicalId
        // 2. Status Update:
        //    - Mark emailStatus as SENT or FAILED based on outcome

        // Since sending email requires external system, we simulate it here.
        // In real implementation, integration with email service would be here.

        // Simulate email sending success:
        entity.setEmailStatus("SENT");
        entity.setEmailSentAt(java.time.Instant.now().toString());

        logger.info("Email sent for jobTechnicalId: {}", entity.getJobTechnicalId());

        return entity;
    }
}
