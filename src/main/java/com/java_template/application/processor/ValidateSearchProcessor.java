package com.java_template.application.processor;

import com.java_template.application.entity.searchrequest.version_1.SearchRequest;
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

import java.util.ArrayList;
import java.util.List;

@Component
public class ValidateSearchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSearchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateSearchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SearchRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(SearchRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SearchRequest entity) {
        return entity != null;
    }

    private SearchRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SearchRequest> context) {
        SearchRequest entity = context.entity();
        // Basic validation rules per functional requirements
        List<String> errors = new ArrayList<>();
        try {
            if (entity.getUserId() == null || entity.getUserId().isEmpty()) {
                errors.add("userId is required");
            }
            Integer page = entity.getPage();
            Integer pageSize = entity.getPageSize();
            if (page == null || page < 1) {
                entity.setPage(1);
            }
            if (pageSize == null) {
                entity.setPageSize(20);
            } else if (entity.getPageSize() > 100) {
                errors.add("pageSize must be <= 100");
                entity.setPageSize(100);
            }

            if (!errors.isEmpty()) {
                // populate validation errors and fail validation by setting state
                try {
                    entity.setValidationErrors(errors);
                } catch (Exception e) {
                    logger.debug("SearchRequest does not expose setValidationErrors(...) - continuing", e);
                }
                entity.setState("VALIDATION_FAILED");
                logger.info("SearchRequest {} validation failed: {}", entity.getTechnicalId(), errors);
                return entity;
            }

            // Validation passed -> move to ingesting
            entity.setState("INGESTING");
            logger.info("SearchRequest {} validated, moving to INGESTING", entity.getTechnicalId());
            return entity;
        } catch (Exception e) {
            logger.error("Error during validation for SearchRequest {}", entity == null ? "<null>" : entity.getTechnicalId(), e);
            // On unexpected errors, mark validation failed
            if (entity != null) {
                entity.setState("VALIDATION_FAILED");
                try { entity.setValidationErrors(List.of("internal_validation_error")); } catch (Exception ignored) {}
            }
            return entity;
        }
    }
}
