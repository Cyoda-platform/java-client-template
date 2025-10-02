package com.java_template.application.criterion;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: This file contains placeholder criteria for workflow compilation.
 * Each criterion provides basic functionality to ensure the system compiles and runs.
 */

@Component
class LoanSettlementCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(LoanSettlementCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public LoanSettlementCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntity(Object.class).map(ctx -> false).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

@Component
class FileValidationPassCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(FileValidationPassCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public FileValidationPassCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntity(Object.class).map(ctx -> true).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

@Component
class FileValidationFailCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(FileValidationFailCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public FileValidationFailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntity(Object.class).map(ctx -> false).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

@Component
class AllPaymentsProcessedCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(AllPaymentsProcessedCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public AllPaymentsProcessedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntity(Object.class).map(ctx -> true).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

@Component
class QuoteExpiryCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(QuoteExpiryCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public QuoteExpiryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntity(Object.class).map(ctx -> false).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

@Component
class SettlementPaymentReceivedCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(SettlementPaymentReceivedCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public SettlementPaymentReceivedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntity(Object.class).map(ctx -> false).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

@Component
class GLAcknowledgmentReceivedCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(GLAcknowledgmentReceivedCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public GLAcknowledgmentReceivedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).toEntity(Object.class).map(ctx -> false).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}
