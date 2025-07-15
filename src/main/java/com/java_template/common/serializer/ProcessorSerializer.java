package com.java_template.common.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Processor serializer interface that integrates with sealed response builders.
 * Provides type-safe response building with sealed interface hierarchy.
 */
public interface ProcessorSerializer {

    /**
     * Extracts a typed entity from the request payload.
     */
    <T extends CyodaEntity> T extractEntity(EntityProcessorCalculationRequest request, Class<T> clazz);

    /**
     * Extracts raw JSON payload from the request.
     */
    JsonNode extractPayload(EntityProcessorCalculationRequest request);

    /**
     * Converts a CyodaEntity to JsonNode.
     * This method allows processors to convert entities for use with withEntity method.
     */
    <T extends CyodaEntity> JsonNode entityToJsonNode(T entity);

    /**
     * Gets the serializer type identifier.
     */
    String getType();

    /**
     * Executes a custom function with the serializer and returns the result.
     * This allows for flexible operations without modifying the interface.
     */
    default <R> R executeFunction(EntityProcessorCalculationRequest request,
                                  Function<ProcessorSerializer, R> function) {
        return function.apply(this);
    }

    /**
     * Creates a response builder for the given request.
     * This builder provides a simplified API for both success and error responses.
     */
    ResponseBuilder.ProcessorResponseBuilder responseBuilder(EntityProcessorCalculationRequest request);

    /**
     * Starts a processing chain with the given request.
     * This allows for a more expressive and chainable API.
     */
    default ProcessingChain withRequest(EntityProcessorCalculationRequest request) {
        return new ProcessingChainImpl(this, request);
    }

    /**
     * Processing chain API for processor operations.
     * Provides a chainable interface for transforming data and building responses.
     */
    interface ProcessingChain {
        /**
         * Maps the extracted payload using the provided function.
         * @param mapper Function to transform the JSON payload
         * @return ProcessingChain for chaining
         */
        ProcessingChain map(Function<JsonNode, JsonNode> mapper);

        /**
         * Extracts an entity and initiates entity-based processing flow.
         * @param clazz Entity class to extract
         * @return EntityProcessingChain for entity-based chaining
         */
        <T extends CyodaEntity> EntityProcessingChain<T> toEntity(Class<T> clazz);

        /**
         * Sets the error handler for the processing chain.
         * @param errorHandler Function to handle errors and create error responses
         * @return ProcessingChain for chaining
         */
        ProcessingChain withErrorHandler(BiFunction<Throwable, JsonNode, ErrorInfo> errorHandler);

        /**
         * Completes the processing chain and returns a response.
         * Uses the error handler if one was set, otherwise uses default error handling.
         * @return The processor response (success or error)
         */
        EntityProcessorCalculationResponse complete();
    }

    /**
     * Entity processing chain API for entity-based processor operations.
     * Provides a chainable interface for transforming entity instances and building responses.
     * This interface supports entity flows where processing operates on entity instances
     * rather than JsonNode objects.
     */
    interface EntityProcessingChain<T extends CyodaEntity> {
        /**
         * Maps the current entity using the provided function.
         * @param mapper Function to transform the entity
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> map(Function<T, T> mapper);

        /**
         * Validates the current entity using the provided predicate.
         * If validation fails, the processing chain will error.
         * @param validator Predicate to validate the entity
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> validate(Function<T, Boolean> validator);

        /**
         * Validates the current entity with a custom error message.
         * @param validator Predicate to validate the entity
         * @param errorMessage Custom error message if validation fails
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> validate(Function<T, Boolean> validator, String errorMessage);

        /**
         * Switches back to JsonNode processing by converting the current entity.
         * @param converter Function to convert entity to JsonNode
         * @return ProcessingChain for JsonNode-based chaining
         */
        ProcessingChain toJsonFlow(Function<T, JsonNode> converter);

        /**
         * Sets the error handler for the entity processing chain.
         * @param errorHandler Function to handle errors and create error responses
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> withErrorHandler(BiFunction<Throwable, T, ErrorInfo> errorHandler);

        /**
         * Completes the entity processing chain and returns the response.
         * Uses the error handler if one was set, otherwise uses default error handling.
         * The entity is automatically converted to JsonNode using the serializer.
         * @return EntityProcessorCalculationResponse
         */
        EntityProcessorCalculationResponse complete();

        /**
         * Completes the entity processing chain with a custom converter.
         * Uses the error handler if one was set, otherwise uses default error handling.
         * @param converter Function to convert the final entity to JsonNode
         * @return EntityProcessorCalculationResponse
         */
        EntityProcessorCalculationResponse complete(Function<T, JsonNode> converter);
    }

    /**
     * Implementation of the ProcessingChain interface.
     */
    class ProcessingChainImpl implements ProcessingChain {
        private final ProcessorSerializer serializer;
        private final EntityProcessorCalculationRequest request;
        private JsonNode processedData;
        private Throwable error;
        private BiFunction<Throwable, JsonNode, ErrorInfo> errorHandler;

        ProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request) {
            this.serializer = serializer;
            this.request = request;
            try {
                this.processedData = serializer.extractPayload(request);
            } catch (Exception e) {
                this.error = e;
            }
        }

        ProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request, JsonNode data) {
            this.serializer = serializer;
            this.request = request;
            this.processedData = data;
            this.error = null;
        }

        ProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request, Throwable error) {
            this.serializer = serializer;
            this.request = request;
            this.processedData = null;
            this.error = error;
        }

        @Override
        public ProcessingChain map(Function<JsonNode, JsonNode> mapper) {
            if (error == null) {
                try {
                    processedData = mapper.apply(processedData);
                } catch (Exception e) {
                    error = e;
                }
            }
            return this;
        }

        @Override
        public <T extends CyodaEntity> EntityProcessingChain<T> toEntity(Class<T> clazz) {
            if (error == null) {
                try {
                    T entity = serializer.extractEntity(request, clazz);
                    return new EntityProcessingChainImpl<>(serializer, request, entity);
                } catch (Exception e) {
                    return new EntityProcessingChainImpl<>(serializer, request, e);
                }
            }
            return new EntityProcessingChainImpl<>(serializer, request, error);
        }

        @Override
        public ProcessingChain withErrorHandler(BiFunction<Throwable, JsonNode, ErrorInfo> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public EntityProcessorCalculationResponse complete() {
            if (error != null) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(error, processedData);
                    return serializer.responseBuilder(request)
                            .withError(errorInfo.code(), errorInfo.message())
                            .build();
                } else {
                    return serializer.responseBuilder(request)
                            .withError("PROCESSING_ERROR", error.getMessage())
                            .build();
                }
            }
            return serializer.responseBuilder(request)
                    .withSuccess(processedData)
                    .build();
        }
    }

    /**
     * Implementation of the EntityProcessingChain interface.
     * Handles entity-based processing flows where operations work on entity instances.
     */
    class EntityProcessingChainImpl<T extends CyodaEntity> implements EntityProcessingChain<T> {
        private final ProcessorSerializer serializer;
        private final EntityProcessorCalculationRequest request;
        private T processedEntity;
        private Throwable error;
        private BiFunction<Throwable, T, ErrorInfo> errorHandler;

        EntityProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request, T entity) {
            this.serializer = serializer;
            this.request = request;
            this.processedEntity = entity;
            this.error = null;
        }

        EntityProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request, Throwable error) {
            this.serializer = serializer;
            this.request = request;
            this.processedEntity = null;
            this.error = error;
        }

        @Override
        public EntityProcessingChain<T> map(Function<T, T> mapper) {
            if (error == null && processedEntity != null) {
                try {
                    processedEntity = mapper.apply(processedEntity);
                } catch (Exception e) {
                    error = e;
                }
            }
            return this;
        }

        @Override
        public EntityProcessingChain<T> validate(Function<T, Boolean> validator) {
            return validate(validator, "Entity validation failed");
        }

        @Override
        public EntityProcessingChain<T> validate(Function<T, Boolean> validator, String errorMessage) {
            if (error == null && processedEntity != null) {
                try {
                    Boolean isValid = validator.apply(processedEntity);
                    if (isValid == null || !isValid) {
                        error = new IllegalArgumentException(errorMessage);
                    }
                } catch (Exception e) {
                    error = e;
                }
            }
            return this;
        }

        @Override
        public ProcessingChain toJsonFlow(Function<T, JsonNode> converter) {
            if (error == null && processedEntity != null) {
                try {
                    JsonNode jsonData = converter.apply(processedEntity);
                    return new ProcessingChainImpl(serializer, request, jsonData);
                } catch (Exception e) {
                    return new ProcessingChainImpl(serializer, request, e);
                }
            }
            return new ProcessingChainImpl(serializer, request, error);
        }

        @Override
        public EntityProcessingChain<T> withErrorHandler(BiFunction<Throwable, T, ErrorInfo> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public EntityProcessorCalculationResponse complete() {
            if (error != null) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(error, processedEntity);
                    return serializer.responseBuilder(request)
                            .withError(errorInfo.code(), errorInfo.message())
                            .build();
                } else {
                    return serializer.responseBuilder(request)
                            .withError(StandardErrorCodes.PROCESSING_ERROR.getCode(), error.getMessage())
                            .build();
                }
            }
            if (processedEntity == null) {
                return serializer.responseBuilder(request)
                        .withError(StandardErrorCodes.PROCESSING_ERROR.getCode(), "Entity is null")
                        .build();
            }
            try {
                JsonNode entityJson = serializer.entityToJsonNode(processedEntity);
                return serializer.responseBuilder(request)
                        .withSuccess(entityJson)
                        .build();
            } catch (Exception e) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(e, processedEntity);
                    return serializer.responseBuilder(request)
                            .withError(errorInfo.code(), errorInfo.message())
                            .build();
                } else {
                    return serializer.responseBuilder(request)
                            .withError("CONVERSION_ERROR", e.getMessage())
                            .build();
                }
            }
        }

        @Override
        public EntityProcessorCalculationResponse complete(Function<T, JsonNode> converter) {
            if (error != null) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(error, processedEntity);
                    return serializer.responseBuilder(request)
                            .withError(errorInfo.code(), errorInfo.message())
                            .build();
                } else {
                    return serializer.responseBuilder(request)
                            .withError("PROCESSING_ERROR", error.getMessage())
                            .build();
                }
            }
            if (processedEntity == null) {
                return serializer.responseBuilder(request)
                        .withError("PROCESSING_ERROR", "Entity is null")
                        .build();
            }
            try {
                JsonNode entityJson = converter.apply(processedEntity);
                return serializer.responseBuilder(request)
                        .withSuccess(entityJson)
                        .build();
            } catch (Exception e) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(e, processedEntity);
                    return serializer.responseBuilder(request)
                            .withError(errorInfo.code(), errorInfo.message())
                            .build();
                } else {
                    return serializer.responseBuilder(request)
                            .withError("CONVERSION_ERROR", e.getMessage())
                            .build();
                }
            }
        }
    }
}
