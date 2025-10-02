package com.java_template.application.criterion;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
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
    private final com.java_template.common.serializer.CriteriaSerializer serializer;

    public LoanSettlementCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).evaluate(ctx -> com.java_template.common.serializer.EvaluationOutcome.fail("Not implemented")).complete();
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
    private final com.java_template.common.serializer.CriteriaSerializer serializer;

    public FileValidationPassCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).evaluate(ctx -> com.java_template.common.serializer.EvaluationOutcome.success()).complete();
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
    private final com.java_template.common.serializer.CriteriaSerializer serializer;

    public FileValidationFailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).evaluate(ctx -> com.java_template.common.serializer.EvaluationOutcome.fail("Validation failed")).complete();
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
    private final com.java_template.common.serializer.CriteriaSerializer serializer;

    public AllPaymentsProcessedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).evaluate(ctx -> com.java_template.common.serializer.EvaluationOutcome.success()).complete();
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
    private final com.java_template.common.serializer.CriteriaSerializer serializer;

    public QuoteExpiryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).evaluate(ctx -> com.java_template.common.serializer.EvaluationOutcome.fail("Quote not expired")).complete();
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
    private final com.java_template.common.serializer.CriteriaSerializer serializer;

    public SettlementPaymentReceivedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).evaluate(ctx -> com.java_template.common.serializer.EvaluationOutcome.fail("Payment not received")).complete();
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
    private final com.java_template.common.serializer.CriteriaSerializer serializer;

    public GLAcknowledgmentReceivedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking {} for request: {}", className, request.getId());
        return serializer.withRequest(request).evaluate(ctx -> com.java_template.common.serializer.EvaluationOutcome.fail("Acknowledgment not received")).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}
