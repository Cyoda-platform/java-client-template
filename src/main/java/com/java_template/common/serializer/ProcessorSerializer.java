package com.java_template.common.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.dto.EntityResponse;
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
     * Context record containing the original request and extracted payload for processor evaluation.
     * Provides access to both request metadata (entityId, transactionId) and payload data.
     */
    record ProcessorExecutionContext(EntityProcessorCalculationRequest request, JsonNode payload) {}

    /**
     * Context record containing the original request and extracted entity for processor evaluation.
     * Provides access to both request metadata (entityId, transactionId) and entity data.
     */
    record ProcessorEntityExecutionContext<T extends CyodaEntity>(EntityProcessorCalculationRequest request, T entity) {}

    /**
     * Context record containing the original request and EntityResponse for processor evaluation.
     * Provides access to both request metadata and complete entity response with metadata.
     */
    record ProcessorEntityResponseExecutionContext<T extends CyodaEntity>(EntityProcessorCalculationRequest request, EntityResponse<T> entityResponse) {}

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
     * Executes a custom function with the serializer and request context.
     * This allows for flexible operations without modifying the interface.
     */
    default <R> R executeFunction(EntityProcessorCalculationRequest request,
                                  Function<ProcessorExecutionContext, R> function) {
        JsonNode payload = extractPayload(request);
        ProcessorExecutionContext context = new ProcessorExecutionContext(request, payload);
        return function.apply(context);
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
         * Maps the extracted payload using the provided function with request context.
         * Provides access to both request metadata and payload data.
         * @param mapper Function to transform the JSON payload with context
         * @return ProcessingChain for chaining
         */
        ProcessingChain map(Function<ProcessorExecutionContext, JsonNode> mapper);

        /**
         * Extracts an entity and initiates entity-based processing flow.
         * @param clazz Entity class to extract
         * @return EntityProcessingChain for entity-based chaining
         */
        <T extends CyodaEntity> EntityProcessingChain<T> toEntity(Class<T> clazz);

        /**
         * Extracts an entity and wraps it in EntityResponse for unified interface processing.
         * This creates a unified interface between processors and controllers.
         * @param clazz Entity class to extract
         * @return EntityResponseProcessingChain for EntityResponse-based chaining
         */
        <T extends CyodaEntity> EntityResponseProcessingChain<T> toEntityResponse(Class<T> clazz);

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
         * Maps the current entity using the provided function with request context.
         * Provides access to both request metadata and entity data.
         * @param mapper Function to transform the entity with context
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> map(Function<ProcessorEntityExecutionContext<T>, T> mapper);

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
     * EntityResponse processing chain API for unified interface processor operations.
     * Provides a chainable interface for transforming EntityResponse instances and building responses.
     * This interface creates consistency between processors and controllers by working with EntityResponse.
     */
    interface EntityResponseProcessingChain<T extends CyodaEntity> {
        /**
         * Maps the current EntityResponse using the provided function with request context.
         * Provides access to both request metadata and complete EntityResponse data.
         * @param mapper Function to transform the EntityResponse with context
         * @return EntityResponseProcessingChain for chaining
         */
        EntityResponseProcessingChain<T> map(Function<ProcessorEntityResponseExecutionContext<T>, EntityResponse<T>> mapper);

        /**
         * Validates the current EntityResponse using the provided predicate.
         * If validation fails, the processing chain will error.
         * @param validator Predicate to validate the EntityResponse
         * @return EntityResponseProcessingChain for chaining
         */
        EntityResponseProcessingChain<T> validate(Function<EntityResponse<T>, Boolean> validator);

        /**
         * Validates the current EntityResponse with a custom error message.
         * @param validator Predicate to validate the EntityResponse
         * @param errorMessage Custom error message if validation fails
         * @return EntityResponseProcessingChain for chaining
         */
        EntityResponseProcessingChain<T> validate(Function<EntityResponse<T>, Boolean> validator, String errorMessage);

        /**
         * Switches to entity processing by extracting the entity from EntityResponse.
         * @return EntityProcessingChain for entity-based chaining
         */
        EntityProcessingChain<T> toEntityFlow();

        /**
         * Sets the error handler for the EntityResponse processing chain.
         * @param errorHandler Function to handle errors and create error responses
         * @return EntityResponseProcessingChain for chaining
         */
        EntityResponseProcessingChain<T> withErrorHandler(BiFunction<Throwable, EntityResponse<T>, ErrorInfo> errorHandler);

        /**
         * Completes the EntityResponse processing chain and returns the response.
         * Uses the error handler if one was set, otherwise uses default error handling.
         * The EntityResponse entity is automatically converted to JsonNode using the serializer.
         * @return EntityProcessorCalculationResponse
         */
        EntityProcessorCalculationResponse complete();

        /**
         * Completes the EntityResponse processing chain with a custom converter.
         * Uses the error handler if one was set, otherwise uses default error handling.
         * @param converter Function to convert the final EntityResponse to JsonNode
         * @return EntityProcessorCalculationResponse
         */
        EntityProcessorCalculationResponse complete(Function<EntityResponse<T>, JsonNode> converter);
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
        public ProcessingChain map(Function<ProcessorExecutionContext, JsonNode> mapper) {
            if (error == null) {
                try {
                    ProcessorExecutionContext context = new ProcessorExecutionContext(request, processedData);
                    processedData = mapper.apply(context);
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
        public <T extends CyodaEntity> EntityResponseProcessingChain<T> toEntityResponse(Class<T> clazz) {
            if (error == null) {
                try {
                    T entity = serializer.extractEntity(request, clazz);
                    // Create EntityResponse with entity and metadata from request
                    EntityResponse<T> entityResponse = createEntityResponseFromRequest(entity, request);
                    return new EntityResponseProcessingChainImpl<>(serializer, request, entityResponse);
                } catch (Exception e) {
                    return new EntityResponseProcessingChainImpl<>(serializer, request, e);
                }
            }
            return new EntityResponseProcessingChainImpl<>(serializer, request, error);
        }

        private <T extends CyodaEntity> EntityResponse<T> createEntityResponseFromRequest(T entity, EntityProcessorCalculationRequest request) {
            // Create EntityMetadata from request
            org.cyoda.cloud.api.event.common.EntityMetadata metadata = new org.cyoda.cloud.api.event.common.EntityMetadata();

            // Convert String entityId to UUID
            try {
                if (request.getEntityId() != null) {
                    metadata.setId(java.util.UUID.fromString(request.getEntityId()));
                }
            } catch (IllegalArgumentException e) {
                // If entityId is not a valid UUID, ignore and continue
            }

            // Extract state from request payload metadata if available
            try {
                if (request.getPayload() != null && request.getPayload().getMeta() != null) {
                    Object state = request.getPayload().getMeta().get("state");
                    if (state != null) {
                        metadata.setState(state.toString());
                    }
                }
            } catch (Exception e) {
                // Ignore metadata extraction errors, continue with basic metadata
            }

            return new EntityResponse<>(entity, metadata);
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
        public EntityProcessingChain<T> map(Function<ProcessorEntityExecutionContext<T>, T> mapper) {
            if (error == null && processedEntity != null) {
                try {
                    ProcessorEntityExecutionContext<T> context = new ProcessorEntityExecutionContext<>(request, processedEntity);
                    processedEntity = mapper.apply(context);
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

    /**
     * Implementation of the EntityResponseProcessingChain interface.
     */
    class EntityResponseProcessingChainImpl<T extends CyodaEntity> implements EntityResponseProcessingChain<T> {
        private final ProcessorSerializer serializer;
        private final EntityProcessorCalculationRequest request;
        private EntityResponse<T> processedEntityResponse;
        private Throwable error;
        private BiFunction<Throwable, EntityResponse<T>, ErrorInfo> errorHandler;

        EntityResponseProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request, EntityResponse<T> entityResponse) {
            this.serializer = serializer;
            this.request = request;
            this.processedEntityResponse = entityResponse;
            this.error = null;
        }

        EntityResponseProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request, Throwable error) {
            this.serializer = serializer;
            this.request = request;
            this.processedEntityResponse = null;
            this.error = error;
        }

        @Override
        public EntityResponseProcessingChain<T> map(Function<ProcessorEntityResponseExecutionContext<T>, EntityResponse<T>> mapper) {
            if (error == null && processedEntityResponse != null) {
                try {
                    ProcessorEntityResponseExecutionContext<T> context = new ProcessorEntityResponseExecutionContext<>(request, processedEntityResponse);
                    EntityResponse<T> result = mapper.apply(context);
                    return new EntityResponseProcessingChainImpl<>(serializer, request, result);
                } catch (Exception e) {
                    return new EntityResponseProcessingChainImpl<>(serializer, request, e);
                }
            }
            return this;
        }

        @Override
        public EntityResponseProcessingChain<T> validate(Function<EntityResponse<T>, Boolean> validator) {
            return validate(validator, "Validation failed");
        }

        @Override
        public EntityResponseProcessingChain<T> validate(Function<EntityResponse<T>, Boolean> validator, String errorMessage) {
            if (error == null && processedEntityResponse != null) {
                try {
                    if (!validator.apply(processedEntityResponse)) {
                        return new EntityResponseProcessingChainImpl<>(serializer, request, new IllegalArgumentException(errorMessage));
                    }
                } catch (Exception e) {
                    return new EntityResponseProcessingChainImpl<>(serializer, request, e);
                }
            }
            return this;
        }

        @Override
        public EntityProcessingChain<T> toEntityFlow() {
            if (error == null && processedEntityResponse != null) {
                T entity = processedEntityResponse.getEntity();
                return new EntityProcessingChainImpl<>(serializer, request, entity);
            }
            return new EntityProcessingChainImpl<>(serializer, request, error != null ? error : new IllegalStateException("EntityResponse is null"));
        }

        @Override
        public EntityResponseProcessingChain<T> withErrorHandler(BiFunction<Throwable, EntityResponse<T>, ErrorInfo> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public EntityProcessorCalculationResponse complete() {
            return complete(entityResponse -> serializer.entityToJsonNode(entityResponse.getEntity()));
        }

        @Override
        public EntityProcessorCalculationResponse complete(Function<EntityResponse<T>, JsonNode> converter) {
            if (error != null) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(error, processedEntityResponse);
                    return serializer.responseBuilder(request)
                            .withError(errorInfo.code(), errorInfo.message())
                            .build();
                } else {
                    return serializer.responseBuilder(request)
                            .withError("PROCESSING_ERROR", error.getMessage())
                            .build();
                }
            }
            if (processedEntityResponse == null) {
                return serializer.responseBuilder(request)
                        .withError("PROCESSING_ERROR", "EntityResponse is null")
                        .build();
            }
            try {
                JsonNode entityJson = converter.apply(processedEntityResponse);
                return serializer.responseBuilder(request)
                        .withSuccess(entityJson)
                        .build();
            } catch (Exception e) {
                if (errorHandler != null) {
                    ErrorInfo errorInfo = errorHandler.apply(e, processedEntityResponse);
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
