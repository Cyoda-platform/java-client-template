package com.example.application.entity.subscription.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Subscription Entity - Recurring billing management
 * Supports subscription plans, schedules, proration, and retry logic
 */
@Data
public class Subscription implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscription";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String subscriptionId;

    // Core subscription fields
    private String merchantId;
    private String customerId;
    private String planId;
    private String planName;
    private BigDecimal planAmount;
    private String currency;

    // Billing schedule
    private String billingCycle; // DAILY, WEEKLY, MONTHLY, YEARLY
    private Integer billingDayOfMonth;
    private LocalDateTime nextBillingDate;
    private LocalDateTime lastBillingDate;

    // Subscription status
    private String status; // ACTIVE, PAUSED, CANCELED, FAILED
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime canceledAt;
    private String cancelReason;

    // Proration & upgrades
    private BigDecimal proratedAmount;
    private String previousPlanId;
    private LocalDateTime upgradeDowngradeDate;

    // Retry policy
    private Integer failedAttempts;
    private Integer maxRetries;
    private LocalDateTime nextRetryDate;
    private String lastFailureReason;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<BillingHistory> billingHistory;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec()
                .withName(ENTITY_NAME)
                .withVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return subscriptionId != null && !subscriptionId.isBlank() &&
               merchantId != null && !merchantId.isBlank() &&
               customerId != null && !customerId.isBlank() &&
               planId != null && !planId.isBlank() &&
               planAmount != null && planAmount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null && !currency.isBlank() &&
               billingCycle != null && !billingCycle.isBlank();
    }

    @Data
    public static class BillingHistory {
        private LocalDateTime billingDate;
        private BigDecimal amount;
        private String status; // SUCCESS, FAILED, PENDING
        private String transactionId;
        private String failureReason;
    }
}

