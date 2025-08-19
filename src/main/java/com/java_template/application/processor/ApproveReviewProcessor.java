package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
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
public class ApproveReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ApproveReviewProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ApproveReview for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product entity) {
        return entity != null;
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();
        try {
            // Approve the manual review: mark product as ACTIVE and clear review flags
            product.setStatus("ACTIVE");
            if (product.getFlags() != null) product.getFlags().clear();
            logger.info("Approved review for productId={}", product.getProductId());
        } catch (Exception e) {
            logger.error("Error approving review for productId={}", product != null ? product.getProductId() : "<unknown>", e);
        }
        // Do not call entityService.updateItem for this entity; Cyoda persists it automatically.
        return product;
    }
}
