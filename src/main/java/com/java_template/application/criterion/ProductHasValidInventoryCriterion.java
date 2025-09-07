package com.java_template.application.criterion;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Product Has Valid Inventory Criterion
 * 
 * Validates that product has valid inventory information including:
 * - Valid SKU (required and unique)
 * - Non-negative quantity available
 * - Positive price
 * - Required name and category
 */
@Component
public class ProductHasValidInventoryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductHasValidInventoryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Product inventory criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateProduct)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for product inventory
     */
    private EvaluationOutcome validateProduct(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product product = context.entityWithMetadata().entity();
        
        // Check if product is null (structural validation)
        if (product == null) {
            logger.warn("Product is null");
            return EvaluationOutcome.fail("Product is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate SKU (required and unique identifier)
        if (product.getSku() == null || product.getSku().trim().isEmpty()) {
            logger.warn("Product has no SKU");
            return EvaluationOutcome.fail("Product SKU is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate quantity available (must be non-negative)
        if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < 0) {
            logger.warn("Product {} has invalid quantity available: {}", product.getSku(), product.getQuantityAvailable());
            return EvaluationOutcome.fail("Product quantity available cannot be negative", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate price (must be positive)
        if (product.getPrice() == null || product.getPrice() <= 0) {
            logger.warn("Product {} has invalid price: {}", product.getSku(), product.getPrice());
            return EvaluationOutcome.fail("Product price must be positive", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate name (required for display)
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            logger.warn("Product {} has no name", product.getSku());
            return EvaluationOutcome.fail("Product name is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate category (required for filtering)
        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            logger.warn("Product {} has no category", product.getSku());
            return EvaluationOutcome.fail("Product category is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate description (required)
        if (product.getDescription() == null || product.getDescription().trim().isEmpty()) {
            logger.warn("Product {} has no description", product.getSku());
            return EvaluationOutcome.fail("Product description is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Additional validations for extended schema if present
        EvaluationOutcome extendedValidation = validateExtendedSchema(product);
        if (!extendedValidation.isSuccess()) {
            return extendedValidation;
        }

        logger.debug("Product {} passed all inventory validation criteria", product.getSku());
        return EvaluationOutcome.success();
    }

    /**
     * Validate extended schema fields if present
     */
    private EvaluationOutcome validateExtendedSchema(Product product) {
        // Validate attributes if present
        if (product.getAttributes() != null) {
            Product.ProductAttributes attributes = product.getAttributes();
            
            // Validate weight if present
            if (attributes.getWeight() != null) {
                if (attributes.getWeight().getValue() == null || attributes.getWeight().getValue() < 0) {
                    logger.warn("Product {} has invalid weight value", product.getSku());
                    return EvaluationOutcome.fail("Product weight value must be non-negative", 
                                                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            }
            
            // Validate dimensions if present
            if (attributes.getDimensions() != null) {
                Product.ProductDimensions dimensions = attributes.getDimensions();
                if ((dimensions.getL() != null && dimensions.getL() < 0) ||
                    (dimensions.getW() != null && dimensions.getW() < 0) ||
                    (dimensions.getH() != null && dimensions.getH() < 0)) {
                    logger.warn("Product {} has invalid dimensions", product.getSku());
                    return EvaluationOutcome.fail("Product dimensions must be non-negative", 
                                                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            }
        }

        // Validate variants if present
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            for (Product.ProductVariant variant : product.getVariants()) {
                if (variant.getVariantSku() == null || variant.getVariantSku().trim().isEmpty()) {
                    logger.warn("Product {} has variant without SKU", product.getSku());
                    return EvaluationOutcome.fail("All product variants must have a SKU", 
                                                StandardEvalReasonCategories.STRUCTURAL_FAILURE);
                }
                
                // Validate price overrides if present
                if (variant.getPriceOverrides() != null && variant.getPriceOverrides().getBase() != null) {
                    if (variant.getPriceOverrides().getBase() <= 0) {
                        logger.warn("Product {} variant {} has invalid price override", product.getSku(), variant.getVariantSku());
                        return EvaluationOutcome.fail("Variant price overrides must be positive", 
                                                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                    }
                }
            }
        }

        // Validate inventory nodes if present
        if (product.getInventory() != null && product.getInventory().getNodes() != null) {
            for (Product.ProductInventoryNode node : product.getInventory().getNodes()) {
                if (node.getNodeId() == null || node.getNodeId().trim().isEmpty()) {
                    logger.warn("Product {} has inventory node without ID", product.getSku());
                    return EvaluationOutcome.fail("All inventory nodes must have an ID", 
                                                StandardEvalReasonCategories.STRUCTURAL_FAILURE);
                }
                
                // Validate quantities in lots
                if (node.getLots() != null) {
                    for (Product.ProductLot lot : node.getLots()) {
                        if (lot.getQty() != null && lot.getQty() < 0) {
                            logger.warn("Product {} has lot with negative quantity", product.getSku());
                            return EvaluationOutcome.fail("Lot quantities cannot be negative", 
                                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                        }
                    }
                }
            }
        }

        return EvaluationOutcome.success();
    }
}
