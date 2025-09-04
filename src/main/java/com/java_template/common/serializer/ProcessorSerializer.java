package com.java_template.common.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.jetbrains.annotations.NotNull;

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
     * Context record containing the original request and EntityWithMetadata for processor evaluation.
     * Provides access to both request metadata and complete entity response with metadata.
     */
    record ProcessorEntityResponseExecutionContext<T extends CyodaEntity>(EntityProcessorCalculationRequest request, EntityWithMetadata<T> entityResponse) {}

    /**
     * Extracts a typed entity from the request payload and wraps it in EntityWithMetadata.
     */
    <T extends CyodaEntity> EntityWithMetadata<T> extractEntity(EntityProcessorCalculationRequest request, Class<T> clazz);

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
         * Extracts an entity and wraps it in EntityWithMetadata for unified interface processing.
         * This creates a unified interface between processors and controllers.
         * @param clazz Entity class to extract
         * @return EntityProcessingChain for EntityWithMetadata-based chaining
         */
        <T extends CyodaEntity> EntityProcessingChain<T> toEntityWithMetadata(Class<T> clazz);

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
     * EntityWithMetadata processing chain API for unified interface processor operations.
     * Provides a chainable interface for transforming EntityWithMetadata instances and building responses.
     * This interface creates consistency between processors and controllers by working with EntityWithMetadata.
     */
    interface EntityProcessingChain<T extends CyodaEntity> {
        /**
         * Maps the current EntityWithMetadata using the provided function with request context.
         * Provides access to both request metadata and complete EntityWithMetadata data.
         * @param mapper Function to transform the EntityWithMetadata with context
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> map(Function<ProcessorEntityResponseExecutionContext<T>, EntityWithMetadata<T>> mapper);

        /**
         * Validates the current EntityWithMetadata using the provided predicate.
         * If validation fails, the processing chain will error.
         * @param validator Predicate to validate the EntityWithMetadata
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> validate(Function<EntityWithMetadata<T>, Boolean> validator);

        /**
         * Validates the current EntityWithMetadata with a custom error message.
         * @param validator Predicate to validate the EntityWithMetadata
         * @param errorMessage Custom error message if validation fails
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> validate(Function<EntityWithMetadata<T>, Boolean> validator, String errorMessage);

        /**
         * Switches back to JsonNode processing by converting the current EntityWithMetadata.
         * @param converter Function to convert EntityWithMetadata to JsonNode
         * @return ProcessingChain for JsonNode-based chaining
         */
        ProcessingChain toJsonFlow(Function<EntityWithMetadata<T>, JsonNode> converter);

        /**
         * Sets the error handler for the EntityWithMetadata processing chain.
         * @param errorHandler Function to handle errors and create error responses
         * @return EntityProcessingChain for chaining
         */
        EntityProcessingChain<T> withErrorHandler(BiFunction<Throwable, EntityWithMetadata<T>, ErrorInfo> errorHandler);

        /**
         * Completes the EntityWithMetadata processing chain and returns the response.
         * Uses the error handler if one was set, otherwise uses default error handling.
         * The EntityWithMetadata entity is automatically converted to JsonNode using the serializer.
         * @return EntityProcessorCalculationResponse
         */
        EntityProcessorCalculationResponse complete();

        /**
         * Completes the EntityWithMetadata processing chain with a custom converter.
         * Uses the error handler if one was set, otherwise uses default error handling.
         * @param converter Function to convert the final EntityWithMetadata to JsonNode
         * @return EntityProcessorCalculationResponse
         */
        EntityProcessorCalculationResponse complete(Function<EntityWithMetadata<T>, JsonNode> converter);
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
        public <T extends CyodaEntity> EntityProcessingChain<T> toEntityWithMetadata(Class<T> clazz) {
            if (error == null) {
                try {
                    EntityWithMetadata<T> entityResponse = serializer.extractEntity(request, clazz);
                    return new EntityProcessingChainImpl<>(serializer, request, entityResponse);
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
     */
    class EntityProcessingChainImpl<T extends CyodaEntity> implements EntityProcessingChain<T> {
        private final ProcessorSerializer serializer;
        private final EntityProcessorCalculationRequest request;
        private final EntityWithMetadata<T> processedEntityResponse;
        private final Throwable error;
        private BiFunction<Throwable, EntityWithMetadata<T>, ErrorInfo> errorHandler;

        EntityProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request, EntityWithMetadata<T> entityResponse) {
            this.serializer = serializer;
            this.request = request;
            this.processedEntityResponse = entityResponse;
            this.error = null;
        }

        EntityProcessingChainImpl(ProcessorSerializer serializer, EntityProcessorCalculationRequest request, Throwable error) {
            this.serializer = serializer;
            this.request = request;
            this.processedEntityResponse = null;
            this.error = error;
        }

        @Override
        public EntityProcessingChain<T> map(Function<ProcessorEntityResponseExecutionContext<T>, EntityWithMetadata<T>> mapper) {
            if (error == null && processedEntityResponse != null) {
                try {
                    ProcessorEntityResponseExecutionContext<T> context = new ProcessorEntityResponseExecutionContext<>(request, processedEntityResponse);
                    EntityWithMetadata<T> result = mapper.apply(context);
                    return new EntityProcessingChainImpl<>(serializer, request, result);
                } catch (Exception e) {
                    return new EntityProcessingChainImpl<>(serializer, request, e);
                }
            }
            return this;
        }

        @Override
        public EntityProcessingChain<T> validate(Function<EntityWithMetadata<T>, Boolean> validator) {
            return validate(validator, "Validation failed");
        }

        @Override
        public EntityProcessingChain<T> validate(Function<EntityWithMetadata<T>, Boolean> validator, String errorMessage) {
            if (error == null && processedEntityResponse != null) {
                try {
                    if (!validator.apply(processedEntityResponse)) {
                        return new EntityProcessingChainImpl<>(serializer, request, new IllegalArgumentException(errorMessage));
                    }
                } catch (Exception e) {
                    return new EntityProcessingChainImpl<>(serializer, request, e);
                }
            }
            return this;
        }

        @Override
        public ProcessingChain toJsonFlow(Function<EntityWithMetadata<T>, JsonNode> converter) {
            if (error == null && processedEntityResponse != null) {
                try {
                    JsonNode jsonNode = converter.apply(processedEntityResponse);
                    return new ProcessingChainImpl(serializer, request, jsonNode);
                } catch (Exception e) {
                    return new ProcessingChainImpl(serializer, request, e);
                }
            }
            return new ProcessingChainImpl(serializer, request, error != null ? error : new IllegalStateException("EntityWithMetadata is null"));
        }

        @Override
        public EntityProcessingChain<T> withErrorHandler(BiFunction<Throwable, EntityWithMetadata<T>, ErrorInfo> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public EntityProcessorCalculationResponse complete() {
            return complete(entityResponse -> serializer.entityToJsonNode(entityResponse.entity()));
        }

        @Override
        public EntityProcessorCalculationResponse complete(Function<EntityWithMetadata<T>, JsonNode> converter) {
            if (error != null) {
                return handleError(error, "PROCESSING_ERROR");
            }
            if (processedEntityResponse == null) {
                return serializer.responseBuilder(request)
                        .withError("PROCESSING_ERROR", "EntityWithMetadata is null")
                        .build();
            }
            if (processedEntityResponse.entity() == null) {
                return serializer.responseBuilder(request)
                        .withError("PROCESSING_ERROR", "Entity is null")
                        .build();
            }
            try {
                JsonNode entityJson = converter.apply(processedEntityResponse);
                return serializer.responseBuilder(request)
                        .withSuccess(entityJson)
                        .build();
            } catch (Exception e) {
                return handleError(e, "CONVERSION_ERROR");
            }
        }

        @NotNull
        private EntityProcessorCalculationResponse handleError(Throwable error, String errorCode) {
            if (errorHandler != null) {
                ErrorInfo errorInfo = errorHandler.apply(error, processedEntityResponse);
                return serializer.responseBuilder(request)
                        .withError(errorInfo.code(), errorInfo.message())
                        .build();
            } else {
                return serializer.responseBuilder(request)
                        .withError(errorCode, error.getMessage())
                        .build();
            }
        }
    }
}
