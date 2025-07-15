package com.java_template.common.serializer;

import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;

/**
 * ABOUTME: Strategy interface for attaching evaluation reasons to responses.
 * Provides abstraction for where and how evaluation reasons are attached to responses,
 * making the system agnostic to the specific attachment mechanism.
 * 
 * Strategy for attaching evaluation reasons to responses.
 * This abstraction allows the EvaluationChain to be agnostic about where
 * the evaluation reason is attached to the response.
 */
@FunctionalInterface
public interface ReasonAttachmentStrategy {
    
    /**
     * Attaches the evaluation reason to the response.
     * 
     * @param response the response to attach the reason to
     * @param reason the evaluation reason to attach
     */
    void attachReason(EntityCriteriaCalculationResponse response, EvaluationReason reason);
    
    /**
     * Default strategy that attaches failure reasons to warnings.
     *
     * @return ReasonAttachmentStrategy that uses warnings
     */
    static ReasonAttachmentStrategy toWarnings() {
        return (response, reason) -> {
            if (response.getWarnings() == null) {
                response.setWarnings(new java.util.ArrayList<>());
            }
            response.getWarnings().add(reason.formatReason());
        };
    }
    
    /**
     * No-op strategy that doesn't attach reasons anywhere.
     * Useful for disabling reason attachment.
     * 
     * @return ReasonAttachmentStrategy that does nothing
     */
    static ReasonAttachmentStrategy none() {
        return (response, reason) -> {
            // No-op: don't attach reasons anywhere
        };
    }
    
    /**
     * Strategy that logs reasons instead of attaching them to the response.
     * Useful for debugging or when reasons should only be logged.
     *
     * @param logger the logger to use
     * @return ReasonAttachmentStrategy that logs reasons
     */
    static ReasonAttachmentStrategy toLogger(org.slf4j.Logger logger) {
        return (response, reason) -> {
            logger.debug("Evaluation failure: {}", reason.formatReason());
        };
    }
    
    /**
     * Composite strategy that applies multiple strategies.
     * 
     * @param strategies the strategies to apply
     * @return ReasonAttachmentStrategy that applies all given strategies
     */
    static ReasonAttachmentStrategy composite(ReasonAttachmentStrategy... strategies) {
        return (response, reason) -> {
            for (ReasonAttachmentStrategy strategy : strategies) {
                strategy.attachReason(response, reason);
            }
        };
    }
}
