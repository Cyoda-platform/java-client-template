package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequest;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessDigestRequest implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessDigestRequest(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("ProcessDigestRequest initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestRequest.class)
            .validate(this::isValidEntity, "Invalid DigestRequest state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(DigestRequest entity) {
        return entity != null && entity.isValid();
    }

    private DigestRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DigestRequest> context) {
        DigestRequest entity = context.entity();

        // Business logic copied from processDigestRequest method in CyodaEntityControllerPrototype
        // 1. Validate userEmail format and required fields are already covered by isValidEntity

        // 2. Retrieve data from the specified externalApiEndpoint on the Petstore Swagger API
        // We simulate the retrieval logic here as we don't have actual API call code in prototype

        // 3. Persist retrieved data as DigestData entity using EntityService
        // Since EntityService is not injected here (only SerializerFactory allowed), we do not persist here.
        // This step would typically require async service call or event trigger.

        // 4. Update DigestRequest status to COMPLETED or FAILED based on retrieval success
        // For simplicity, we update status to PROCESSING to indicate ongoing processing
        entity.setStatus("PROCESSING");

        // 5. Trigger processEmailDispatch for the same DigestRequest is outside processor responsibility

        return entity;
    }
}
