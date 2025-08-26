package com.java_template.application.processor;
import com.java_template.application.entity.searchfilter.version_1.SearchFilter;
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

@Component
public class ActivateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ActivateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SearchFilter for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(SearchFilter.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SearchFilter entity) {
        return entity != null && entity.isValid();
    }

    private SearchFilter processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SearchFilter> context) {
        SearchFilter entity = context.entity();

        // If already active, nothing to do
        if (Boolean.TRUE.equals(entity.getIsActive())) {
            logger.info("SearchFilter {} is already active", entity.getId());
            return entity;
        }

        // Apply sensible defaults where appropriate before activation
        if (entity.getPageSize() == null || entity.getPageSize() <= 0) {
            entity.setPageSize(20); // default page size
            logger.debug("Applied default pageSize=20 for SearchFilter {}", entity.getId());
        }

        if (entity.getRadiusKm() == null || entity.getRadiusKm() < 0) {
            entity.setRadiusKm(30); // default radius
            logger.debug("Applied default radiusKm=30 for SearchFilter {}", entity.getId());
        }

        // Ensure location center is present and has coordinates (entity.isValid() already checked this,
        // but double-checking to avoid accidental activation on malformed data)
        if (entity.getLocationCenter() == null
            || entity.getLocationCenter().getLat() == null
            || entity.getLocationCenter().getLon() == null) {
            logger.warn("SearchFilter {} missing location center coordinates; cannot activate", entity.getId());
            return entity;
        }

        // Mark filter as active
        entity.setIsActive(Boolean.TRUE);
        logger.info("Activated SearchFilter {}", entity.getId());

        return entity;
    }
}