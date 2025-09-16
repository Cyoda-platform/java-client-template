package com.java_template.application.criterion;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Criterion to check API rate limits for external service calls.
 * Used for Cat Fact API calls and other external integrations.
 * 
 * Validation Logic:
 * - Checks if API calls in last minute < 60 (per-minute limit)
 * - Checks if API calls in last hour < 1000 (per-hour limit)
 * - Tracks rate limits per API endpoint
 * - Returns success if within rate limits
 */
@Component
public class ApiRateLimitCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(ApiRateLimitCriterion.class);
    private static final int PER_MINUTE_LIMIT = 60;
    private static final int PER_HOUR_LIMIT = 1000;
    
    // In-memory rate limit tracking (in production, use Redis or similar)
    private static final ConcurrentHashMap<String, RateLimitTracker> rateLimitTrackers = new ConcurrentHashMap<>();
    
    private final CriterionSerializer serializer;

    public ApiRateLimitCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("ApiRateLimitCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking API rate limit criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluate(ctx -> this.evaluateApiRateLimit())
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "ApiRateLimitCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether API rate limits are within acceptable bounds.
     * 
     * @return EvaluationOutcome indicating whether rate limits are OK
     */
    private EvaluationOutcome evaluateApiRateLimit() {
        String apiEndpoint = "catfact.ninja"; // Primary external API we use
        
        RateLimitTracker tracker = rateLimitTrackers.computeIfAbsent(apiEndpoint, 
            k -> new RateLimitTracker());
        
        LocalDateTime now = LocalDateTime.now();
        
        // Clean up old entries
        tracker.cleanup(now);
        
        // Check per-minute limit
        int callsLastMinute = tracker.getCallsInLastMinute(now);
        if (callsLastMinute >= PER_MINUTE_LIMIT) {
            return EvaluationOutcome.fail(String.format(
                "API rate limit exceeded: %d calls in last minute (limit: %d)", 
                callsLastMinute, PER_MINUTE_LIMIT));
        }
        
        // Check per-hour limit
        int callsLastHour = tracker.getCallsInLastHour(now);
        if (callsLastHour >= PER_HOUR_LIMIT) {
            return EvaluationOutcome.fail(String.format(
                "API rate limit exceeded: %d calls in last hour (limit: %d)", 
                callsLastHour, PER_HOUR_LIMIT));
        }
        
        // Record this check as a potential API call
        tracker.recordCall(now);
        
        // Rate limits are within bounds
        logger.debug("API rate limits OK for {}: {}m calls: {}, {}h calls: {}", 
                    apiEndpoint, 1, callsLastMinute, 1, callsLastHour);
        return EvaluationOutcome.success();
    }

    /**
     * Rate limit tracker for a specific API endpoint.
     */
    private static class RateLimitTracker {
        private final ConcurrentHashMap<LocalDateTime, AtomicInteger> callCounts = new ConcurrentHashMap<>();
        
        /**
         * Records an API call at the specified time.
         */
        public void recordCall(LocalDateTime timestamp) {
            // Round down to the minute for tracking
            LocalDateTime minute = timestamp.truncatedTo(ChronoUnit.MINUTES);
            callCounts.computeIfAbsent(minute, k -> new AtomicInteger(0)).incrementAndGet();
        }
        
        /**
         * Gets the number of calls in the last minute.
         */
        public int getCallsInLastMinute(LocalDateTime now) {
            LocalDateTime oneMinuteAgo = now.minus(1, ChronoUnit.MINUTES);
            return callCounts.entrySet().stream()
                .filter(entry -> entry.getKey().isAfter(oneMinuteAgo))
                .mapToInt(entry -> entry.getValue().get())
                .sum();
        }
        
        /**
         * Gets the number of calls in the last hour.
         */
        public int getCallsInLastHour(LocalDateTime now) {
            LocalDateTime oneHourAgo = now.minus(1, ChronoUnit.HOURS);
            return callCounts.entrySet().stream()
                .filter(entry -> entry.getKey().isAfter(oneHourAgo))
                .mapToInt(entry -> entry.getValue().get())
                .sum();
        }
        
        /**
         * Cleans up old entries to prevent memory leaks.
         */
        public void cleanup(LocalDateTime now) {
            LocalDateTime cutoff = now.minus(2, ChronoUnit.HOURS); // Keep 2 hours of history
            callCounts.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoff));
        }
    }
}
