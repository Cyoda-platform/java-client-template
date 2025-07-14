package com.java_template.common.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Criterion serializer interface that integrates with sealed response builders.
 * Provides type-safe response building with sealed interface hierarchy.
 */
public interface CriterionSerializer {

    /**
     * Extracts a typed entity from the request payload.
     */
    <T extends CyodaEntity> T extractEntity(EntityCriteriaCalculationRequest request, Class<T> clazz);

    /**
     * Extracts raw JSON payload from the request.
     */
    JsonNode extractPayload(EntityCriteriaCalculationRequest request);

    /**
     * Converts a CyodaEntity to JsonNode.
     * This method allows criteria to convert entities for validation purposes.
     */
    <T extends CyodaEntity> JsonNode entityToJsonNode(T entity);

    /**
     * Gets the serializer type identifier.
     */
    String getType();
    
    /**
     * Creates a response builder for the given request.
     * This builder provides a simplified API for both match and error responses.
     */
    ResponseBuilder.CriterionResponseBuilder responseBuilder(EntityCriteriaCalculationRequest request);

    /**
     * Starts an evaluation chain with the given request.
     * This allows for a more expressive and chainable API.
     */
    default EvaluationChain withRequest(EntityCriteriaCalculationRequest request) {
        return new EvaluationChainImpl(this, request);
    }

    /**
     * Evaluation chain API for criterion operations.
     * Provides a chainable interface for evaluating criteria and building responses.
     */
    interface EvaluationChain {
        /**
         * Evaluates the criterion using the provided predicate on the JSON payload.
         * @param evaluator Predicate to evaluate the JSON payload
         * @return EvaluationChain for chaining
         */
        EvaluationChain evaluate(Predicate<JsonNode> evaluator);

        /**
         * Evaluates the criterion using the provided predicate on the extracted entity.
         * @param clazz Entity class to extract
         * @param evaluator Predicate to evaluate the entity
         * @return EvaluationChain for chaining
         */
        <T extends CyodaEntity> EvaluationChain evaluateEntity(Class<T> clazz, Predicate<T> evaluator);

        /**
         * Evaluates the criterion with a reason provider for the JSON payload.
         * @param evaluator Function that returns both result and reason
         * @return EvaluationChain for chaining
         */
        EvaluationChain evaluateWithReason(Function<JsonNode, EvaluationReason> evaluator);

        /**
         * Evaluates the criterion with a reason provider for the extracted entity.
         * @param clazz Entity class to extract
         * @param evaluator Function that returns both result and reason
         * @return EvaluationChain for chaining
         */
        <T extends CyodaEntity> EvaluationChain evaluateEntityWithReason(Class<T> clazz, Function<T, EvaluationReason> evaluator);

        /**
         * Sets the error handler for the evaluation chain.
         * @param errorHandler Function to handle errors and create error responses
         * @return EvaluationChain for chaining
         */
        EvaluationChain withErrorHandler(BiFunction<Throwable, JsonNode, ErrorInfo> errorHandler);

        /**
         * Sets the reason attachment strategy for the evaluation chain.
         * @param strategy Strategy for attaching evaluation reasons to responses
         * @return EvaluationChain for chaining
         */
        EvaluationChain withReasonAttachment(ReasonAttachmentStrategy strategy);

        /**
         * Completes the evaluation chain and returns the appropriate response.
         * Uses the error handler if one was set, otherwise uses default error handling.
         * @return The criterion response (match, non-match, or error)
         */
        EntityCriteriaCalculationResponse complete();
    }

    /**
     * Implementation of the EvaluationChain interface.
     */
    class EvaluationChainImpl implements EvaluationChain {
        private final CriterionSerializer serializer;
        private final EntityCriteriaCalculationRequest request;
        private JsonNode payload;
        private Throwable error;
        private Boolean matches;
        private EvaluationReason evaluationReason;
        private BiFunction<Throwable, JsonNode, ErrorInfo> errorHandler;
        private ReasonAttachmentStrategy reasonAttachmentStrategy = ReasonAttachmentStrategy.toWarnings();

        EvaluationChainImpl(CriterionSerializer serializer, EntityCriteriaCalculationRequest request) {
            this.serializer = serializer;
            this.request = request;
            try {
                this.payload = serializer.extractPayload(request);
            } catch (Exception e) {
                this.error = e;
            }
        }

        @Override
        public EvaluationChain evaluate(Predicate<JsonNode> evaluator) {
            if (error == null && matches == null) {
                try {
                    matches = evaluator.test(payload);
                } catch (Exception e) {
                    error = e;
                }
            }
            return this;
        }

        @Override
        public <T extends CyodaEntity> EvaluationChain evaluateEntity(Class<T> clazz, Predicate<T> evaluator) {
            if (error == null && matches == null) {
                try {
                    T entity = serializer.extractEntity(request, clazz);
                    matches = evaluator.test(entity);
                } catch (Exception e) {
                    error = e;
                }
            }
            return this;
        }

        @Override
        public EvaluationChain evaluateWithReason(Function<JsonNode, EvaluationReason> evaluator) {
            if (error == null && matches == null) {
                try {
                    evaluationReason = evaluator.apply(payload);
                    matches = false; // EvaluationReason only exists for failures
                } catch (Exception e) {
                    error = e;
                }
            }
            return this;
        }

        @Override
        public <T extends CyodaEntity> EvaluationChain evaluateEntityWithReason(Class<T> clazz, Function<T, EvaluationReason> evaluator) {
            if (error == null && matches == null) {
                try {
                    T entity = serializer.extractEntity(request, clazz);
                    evaluationReason = evaluator.apply(entity);
                    matches = evaluationReason == null; // null means success, non-null means failure
                } catch (Exception e) {
                    error = e;
                }
            }
            return this;
        }

        @Override
        public EvaluationChain withErrorHandler(BiFunction<Throwable, JsonNode, ErrorInfo> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public EvaluationChain withReasonAttachment(ReasonAttachmentStrategy strategy) {
            this.reasonAttachmentStrategy = strategy;
            return this;
        }

        @Override
        public EntityCriteriaCalculationResponse complete() {
            if (error != null) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(error, payload);
                    return serializer.responseBuilder(request)
                            .withError(errorInfo.code(), errorInfo.message())
                            .build();
                } else {
                    return serializer.responseBuilder(request)
                            .withError("EVALUATION_ERROR", error.getMessage())
                            .build();
                }
            }

            if (matches == null) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(
                        new IllegalStateException("No evaluation was performed"),
                        payload
                    );
                    return serializer.responseBuilder(request)
                            .withError(errorInfo.code(), errorInfo.message())
                            .build();
                } else {
                    return serializer.responseBuilder(request)
                            .withError(StandardErrorCodes.EVALUATION_ERROR.getCode(), "No evaluation was performed")
                            .build();
                }
            }

            // Build the response
            EntityCriteriaCalculationResponse response = matches ?
                serializer.responseBuilder(request).withMatch().build() :
                serializer.responseBuilder(request).withNonMatch().build();

            // Attach evaluation reason if present (only for failures)
            if (evaluationReason != null && reasonAttachmentStrategy != null) {
                reasonAttachmentStrategy.attachReason(response, evaluationReason);
            }

            return response;
        }
    }
}
