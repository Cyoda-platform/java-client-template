#\!/bin/bash

# Create Investigator processors
investigator_processors=(
    "InvestigatorCredentialsProcessor"
    "InvestigatorVerificationProcessor"
    "InvestigatorRejectionProcessor"
    "InvestigatorSiteAssignmentProcessor"
    "InvestigatorStudyAssignmentProcessor"
    "InvestigatorActivationProcessor"
    "InvestigatorPerformanceProcessor"
    "InvestigatorTrainingProcessor"
    "InvestigatorSuspensionProcessor"
    "InvestigatorCompletionProcessor"
    "InvestigatorReactivationProcessor"
    "InvestigatorTerminationProcessor"
)

for processor in "${investigator_processors[@]}"; do
    cat > "src/main/java/com/java_template/application/processor/${processor}.java" << PROC_EOF
package com.java_template.application.processor;

import com.java_template.application.entity.investigator.version_1.Investigator;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class ${processor} implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(${processor}.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ${processor}(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Investigator.class)
                .validate(this::isValidEntityWithMetadata, "Invalid investigator entity wrapper")
                .map(this::processEntity)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Investigator> entityWithMetadata) {
        return entityWithMetadata \!= null && entityWithMetadata.entity() \!= null && entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Investigator> processEntity(EntityWithMetadata<Investigator> entityWithMetadata) {
        Investigator investigator = entityWithMetadata.entity();
        logger.info("Processing {} for investigator: {}", className, investigator.getInvestigatorId());
        investigator.setUpdatedAt(LocalDateTime.now());
        return entityWithMetadata;
    }
}
PROC_EOF
done

# Create Protocol processors
protocol_processors=(
    "ProtocolUpdateProcessor"
    "ProtocolSubmissionProcessor"
    "ProtocolApprovalProcessor"
    "ProtocolRevisionProcessor"
    "ProtocolRejectionProcessor"
    "ProtocolActivationProcessor"
    "ProtocolAmendmentProcessor"
    "ProtocolSuspensionProcessor"
    "ProtocolCompletionProcessor"
    "ProtocolAmendmentApprovalProcessor"
    "ProtocolAmendmentRejectionProcessor"
    "ProtocolResumptionProcessor"
    "ProtocolTerminationProcessor"
)

for processor in "${protocol_processors[@]}"; do
    cat > "src/main/java/com/java_template/application/processor/${processor}.java" << PROC_EOF
package com.java_template.application.processor;

import com.java_template.application.entity.protocol.version_1.Protocol;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class ${processor} implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(${processor}.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ${processor}(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Protocol.class)
                .validate(this::isValidEntityWithMetadata, "Invalid protocol entity wrapper")
                .map(this::processEntity)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Protocol> entityWithMetadata) {
        return entityWithMetadata \!= null && entityWithMetadata.entity() \!= null && entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Protocol> processEntity(EntityWithMetadata<Protocol> entityWithMetadata) {
        Protocol protocol = entityWithMetadata.entity();
        logger.info("Processing {} for protocol: {}", className, protocol.getProtocolId());
        protocol.setUpdatedAt(LocalDateTime.now());
        return entityWithMetadata;
    }
}
PROC_EOF
done

echo "Created Investigator and Protocol processors"
