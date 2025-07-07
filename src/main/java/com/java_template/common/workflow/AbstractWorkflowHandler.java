package com.java_template.common.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Abstract base class for workflow handlers that provides automatic method discovery.
 * Uses reflection to find methods annotated with @WorkflowMethod and registers them automatically.
 *
 * @param <T> the entity type this workflow handles
 */
public abstract class AbstractWorkflowHandler<T extends WorkflowEntity> implements WorkflowHandler<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, Function<T, CompletableFuture<T>>> methodDispatcher;

    protected AbstractWorkflowHandler() {
        this.methodDispatcher = discoverWorkflowMethods();
    }

    /**
     * Automatically discovers methods annotated with @WorkflowMethod.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Function<T, CompletableFuture<T>>> discoverWorkflowMethods() {
        Map<String, Function<T, CompletableFuture<T>>> methods = new HashMap<>();

        for (Method method : this.getClass().getDeclaredMethods()) {
            WorkflowMethod annotation = method.getAnnotation(WorkflowMethod.class);
            if (annotation != null) {
                String methodName = annotation.value().isEmpty() ? method.getName() : annotation.value();

                // Validate method signature
                if (method.getParameterCount() == 1 &&
                    method.getParameterTypes()[0].isAssignableFrom(getEntityClass()) &&
                    CompletableFuture.class.isAssignableFrom(method.getReturnType())) {

                    method.setAccessible(true);
                    Function<T, CompletableFuture<T>> methodFunction = entity -> {
                        try {
                            return (CompletableFuture<T>) method.invoke(this, entity);
                        } catch (Exception e) {
                            logger.error("Error invoking workflow method '{}': {}", methodName, e.getMessage(), e);
                            return CompletableFuture.failedFuture(e);
                        }
                    };

                    methods.put(methodName, methodFunction);
                    logger.debug("Registered workflow method: {} ({})", methodName, annotation.description());
                } else {
                    logger.warn("Method '{}' has @WorkflowMethod but invalid signature", method.getName());
                }
            }
        }

        return methods;
    }

    /**
     * Subclasses must provide the entity class for type checking.
     */
    protected abstract Class<T> getEntityClass();
    
    @Override
    public final CompletableFuture<T> processEntity(T entity, String methodName) {
        Function<T, CompletableFuture<T>> method = methodDispatcher.get(methodName);
        if (method == null) {
            logger.warn("Unknown method '{}' for entity type '{}'", methodName, getEntityType());
            return CompletableFuture.completedFuture(entity);
        }

        return method.apply(entity);
    }

    @Override
    public final String[] getAvailableMethods() {
        return methodDispatcher.keySet().toArray(new String[0]);
    }
    

}
