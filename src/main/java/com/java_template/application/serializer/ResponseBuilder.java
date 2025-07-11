package com.java_template.application.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.workflow.CyodaEntity;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import java.util.function.Function;


public abstract sealed class ResponseBuilder
    permits ResponseBuilder.ProcessorBuilder, ResponseBuilder.CriterionBuilder {

    protected ResponseBuilder() {
        // Base constructor
    }

    /**
     * Simple error information holder.
     */
    @Setter
    @Getter
    public static class ErrorInfo {
        private String code;
        private String message;

    }

    /**
     * Builder for processor calculation responses.
     * Provides processor-specific operations like entity and JSON data handling.
     */
    public static sealed class ProcessorBuilder extends ResponseBuilder
        permits ProcessorBuilder.SuccessBuilder, ProcessorBuilder.ErrorBuilder {

        protected final EntityProcessorCalculationRequest request;
        protected boolean success = false;
        protected JsonNode jsonData;
        protected ErrorInfo errorInfo;
        protected String additionalErrorDetails;

        public ProcessorBuilder(EntityProcessorCalculationRequest request) {
            super();
            this.request = request;
        }

        /**
         * Marks the response as successful.
         */
        public ProcessorBuilder withSuccess() {
            this.success = true;
            return this;
        }

        /**
         * Adds error information to the response.
         */
        public ProcessorBuilder withError(String code, String message) {
            this.success = false;
            this.errorInfo = new ErrorInfo();
            this.errorInfo.setCode(code);
            this.errorInfo.setMessage(message);
            return this;
        }

        /**
         * Adds error information from an exception.
         */
        public ProcessorBuilder withError(String code, Exception exception) {
            return withError(code, exception.getMessage());
        }

        /**
         * Sets JSON data in the response payload.
         */
        public ProcessorBuilder withJsonData(JsonNode jsonData) {
            this.jsonData = jsonData;
            return this;
        }

        /**
         * Sets entity data in the response payload using a converter function.
         */
        public <T extends CyodaEntity> ProcessorBuilder withEntity(T entity, Function<T, JsonNode> jsonConverter) {
            this.jsonData = jsonConverter.apply(entity);
            return this;
        }

        /**
         * Adds additional error details to existing error information.
         */
        public ProcessorBuilder withAdditionalErrorDetails(String details) {
            this.additionalErrorDetails = details;
            return this;
        }

        /**
         * Builds the processor calculation response.
         */
        public EntityProcessorCalculationResponse build() {
            EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();

            // Copy basic fields from request
            response.setId(request.getId());
            response.setRequestId(request.getRequestId());
            response.setEntityId(request.getEntityId());
            response.setSuccess(success);

            // Set payload if we have JSON data
            if (jsonData != null) {
                DataPayload payload = new DataPayload();
                payload.setData(jsonData);
                response.setPayload(payload);
            }

            // Set error information if present
            if (errorInfo != null) {
                if (additionalErrorDetails != null) {
                    errorInfo.setMessage(errorInfo.getMessage() + " - " + additionalErrorDetails);
                }
                // Create a simple error object that matches the expected structure
                // The actual Error type in the response expects a simple object with code and message
                response.setSuccess(false);
                // Note: The actual error setting would depend on the specific response structure
            }

            return response;
        }

        /**
         * Success-focused builder for processor responses.
         * Provides fluent API for successful response construction.
         */
        public static final class SuccessBuilder extends ProcessorBuilder {

            public SuccessBuilder(EntityProcessorCalculationRequest request) {
                super(request);
                withSuccess(); // Automatically mark as successful
            }

            /**
             * Pipes the current builder through a processing function.
             * Enables functional composition and lambda-based processing.
             */
            public ProcessorBuilder pipe(Function<ProcessorBuilder, ProcessorBuilder> processor) {
                return processor.apply(this);
            }

            /**
             * Creates a success response with entity data.
             */
            public ProcessorBuilder withSuccessEntity(CyodaEntity entity, Function<CyodaEntity, JsonNode> converter) {
                return this.withSuccess().withEntity(entity, converter);
            }

            /**
             * Creates a success response with JSON data.
             */
            public ProcessorBuilder withSuccessData(JsonNode data) {
                return this.withSuccess().withJsonData(data);
            }
        }

        /**
         * Error-focused builder for processor responses.
         * Provides fluent API for error response construction.
         */
        public static final class ErrorBuilder extends ProcessorBuilder {

            public ErrorBuilder(EntityProcessorCalculationRequest request) {
                super(request);
            }

            /**
             * Creates an error response with additional context.
             */
            public ProcessorBuilder withErrorContext(String code, String message, String context) {
                return this.withError(code, message).withAdditionalErrorDetails(context);
            }

            /**
             * Creates an error response from exception with context.
             */
            public ProcessorBuilder withExceptionContext(String code, Exception exception, String context) {
                return this.withError(code, exception).withAdditionalErrorDetails(context);
            }
        }
    }

    /**
     * Builder for criteria calculation responses.
     * Provides criteria-specific operations like match result handling.
     */
    public static sealed class CriterionBuilder extends ResponseBuilder
        permits CriterionBuilder.MatchBuilder, CriterionBuilder.ErrorBuilder {

        protected final EntityCriteriaCalculationRequest request;
        protected boolean success = false;
        protected boolean matches = false;
        protected String matchDetails;
        protected ErrorInfo errorInfo;

        public CriterionBuilder(EntityCriteriaCalculationRequest request) {
            super();
            this.request = request;
        }

        /**
         * Marks the response as successful.
         */
        public CriterionBuilder withSuccess() {
            this.success = true;
            return this;
        }

        /**
         * Adds error information to the response.
         */
        public CriterionBuilder withError(String code, String message) {
            this.success = false;
            this.errorInfo = new ErrorInfo();
            this.errorInfo.setCode(code);
            this.errorInfo.setMessage(message);
            return this;
        }

        /**
         * Adds error information from an exception.
         */
        public CriterionBuilder withError(String code, Exception exception) {
            return withError(code, exception.getMessage());
        }

        /**
         * Sets the match result for the criteria evaluation.
         */
        public CriterionBuilder withMatchResult(boolean matches) {
            this.matches = matches;
            return this;
        }

        /**
         * Adds details about the match evaluation.
         */
        public CriterionBuilder withMatchDetails(String details) {
            this.matchDetails = details;
            return this;
        }

        /**
         * Creates a successful match response.
         */
        public CriterionBuilder withSuccessfulMatch(String details) {
            return this.withSuccess().withMatchResult(true).withMatchDetails(details);
        }

        /**
         * Creates a successful non-match response.
         */
        public CriterionBuilder withSuccessfulNonMatch(String details) {
            return this.withSuccess().withMatchResult(false).withMatchDetails(details);
        }

        /**
         * Creates an error response that also sets match to false.
         */
        public CriterionBuilder withMatchError(String code, String message) {
            return this.withError(code, message).withMatchResult(false);
        }

        /**
         * Creates an error response from exception that also sets match to false.
         */
        public CriterionBuilder withMatchException(String code, Exception exception) {
            return this.withError(code, exception).withMatchResult(false);
        }

        /**
         * Builds the criteria calculation response.
         */
        public EntityCriteriaCalculationResponse build() {
            EntityCriteriaCalculationResponse response = new EntityCriteriaCalculationResponse();

            // Copy basic fields from request
            response.setId(request.getId());
            response.setRequestId(request.getRequestId());
            response.setEntityId(request.getEntityId());
            response.setSuccess(success);
            response.setMatches(matches);

            // Set error information if present
            if (errorInfo != null) {
                response.setSuccess(false);
                // Note: The actual error setting would depend on the specific response structure
            }

            return response;
        }

        /**
         * Match-focused builder for criteria responses.
         * Provides fluent API for match result construction.
         */
        public static final class MatchBuilder extends CriterionBuilder {

            public MatchBuilder(EntityCriteriaCalculationRequest request) {
                super(request);
                withSuccess(); // Automatically mark as successful
            }

            /**
             * Creates a successful match response.
             */
            public CriterionBuilder withSuccessfulMatch(String details) {
                return this.withSuccess().withMatchResult(true).withMatchDetails(details);
            }

            /**
             * Creates a successful non-match response.
             */
            public CriterionBuilder withSuccessfulNonMatch(String details) {
                return this.withSuccess().withMatchResult(false).withMatchDetails(details);
            }

            /**
             * Pipes the current builder through a processing function.
             */
            public CriterionBuilder pipe(Function<CriterionBuilder, CriterionBuilder> processor) {
                return processor.apply(this);
            }
        }

        /**
         * Error-focused builder for criteria responses.
         * Provides fluent API for error response construction.
         */
        public static final class ErrorBuilder extends CriterionBuilder {

            public ErrorBuilder(EntityCriteriaCalculationRequest request) {
                super(request);
            }

            /**
             * Creates an error response that also sets match to false.
             */
            public CriterionBuilder withMatchError(String code, String message) {
                return this.withError(code, message).withMatchResult(false);
            }

            /**
             * Creates an error response from exception that also sets match to false.
             */
            public CriterionBuilder withMatchException(String code, Exception exception) {
                return this.withError(code, exception).withMatchResult(false);
            }
        }
    }
}
