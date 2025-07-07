package com.java_template.common.workflow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods as workflow methods.
 * Methods annotated with this will be automatically discovered and registered.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowMethod {
    
    /**
     * The name of the workflow method. If not specified, uses the method name.
     * @return the workflow method name
     */
    String value() default "";
    
    /**
     * Description of what this workflow method does.
     * @return method description
     */
    String description() default "";
}
