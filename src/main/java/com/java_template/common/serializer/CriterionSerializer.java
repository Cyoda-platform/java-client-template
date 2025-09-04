package com.java_template.common.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Criterion serializer interface that integrates with sealed response builders.
 * Provides type-safe response building with sealed interface hierarchy.
 */
public interface CriterionSerializer {

    /**
     * Context record containing the original request and extracted payload for criterion evaluation.
     * Provides access to both request metadata (entityId, transactionId) and payload data.
     */
    record CriterionEvaluationContext(EntityCriteriaCalculationRequest request, JsonNode payload) {}

    /**
     * Context record containing the original request and extracted entity with metadata for criterion evaluation.
     * Provides access to both request metadata (entityId, transactionId) and entity data with technical metadata.
     */
    record CriterionEntityEvaluationContext<T extends CyodaEntity>(EntityCriteriaCalculationRequest request, EntityWithMetadata<T> entityWithMetadata) {}

    /**
     * Extracts a typed entity from the request payload and wraps it in EntityWithMetadata.
     * Provides access to both entity data and technical metadata (id, state, etc.).
     */
    <T extends CyodaEntity> EntityWithMetadata<T> extractEntityWithMetadata(EntityCriteriaCalculationRequest request, Class<T> clazz);

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
         * Evaluates the criterion with an outcome provider for the JSON payload with request context.
         * Provides access to both request metadata and payload data.
         * @param evaluator Function that returns EvaluationOutcome (Success or Fail)
         * @return EvaluationChain for chaining
         */
        EvaluationChain evaluate(Function<CriterionEvaluationContext, EvaluationOutcome> evaluator);

        /**
         * Evaluates the criterion with an outcome provider for the extracted entity with request context.
         * Provides access to both request metadata and entity data.
         * @param clazz Entity class to extract
         * @param evaluator Function that returns EvaluationOutcome (Success or Fail)
         * @return EvaluationChain for chaining
         */
        <T extends CyodaEntity> EvaluationChain evaluateEntity(Class<T> clazz, Function<CriterionEntityEvaluationContext<T>, EvaluationOutcome> evaluator);

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
        public EvaluationChain evaluate(Function<CriterionEvaluationContext, EvaluationOutcome> evaluator) {
            if (error == null && matches == null) {
                try {
                    CriterionEvaluationContext context = new CriterionEvaluationContext(request, payload);
                    EvaluationOutcome outcome = evaluator.apply(context);
                    if (outcome instanceof EvaluationOutcome.Success) {
                        matches = true;
                        evaluationReason = null;
                    } else if (outcome instanceof EvaluationOutcome.Fail fail) {
                        matches = false;
                        evaluationReason = fail.toEvaluationReason();
                    }
                } catch (Exception e) {
                    error = e;
                }
            }
            return this;
        }

        @Override
        public <T extends CyodaEntity> EvaluationChain evaluateEntity(Class<T> clazz, Function<CriterionEntityEvaluationContext<T>, EvaluationOutcome> evaluator) {
            if (error == null && matches == null) {
                try {
                    EntityWithMetadata<T> entityWithMetadata = serializer.extractEntityWithMetadata(request, clazz);
                    CriterionEntityEvaluationContext<T> context = new CriterionEntityEvaluationContext<>(request, entityWithMetadata);
                    EvaluationOutcome outcome = evaluator.apply(context);
                    if (outcome instanceof EvaluationOutcome.Success) {
                        matches = true;
                        evaluationReason = null;
                    } else if (outcome instanceof EvaluationOutcome.Fail fail) {
                        matches = false;
                        evaluationReason = fail.toEvaluationReason();
                    }
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
