package org.cyoda.cloud.api.event.common.condition;

import com.fasterxml.jackson.annotation.*;

/**
 * ABOUTME: Base class for the generated query conditions.
 *
 * jsonschema2pojo doesn't support the generation of discriminator-related annotations, so this is the workaround
 * and we just using 'existingJavaType' in json-schema to declare inheritance.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type"
})
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = GroupCondition.class, name = "group"),
    @JsonSubTypes.Type(value = SimpleCondition.class, name = "simple"),
    @JsonSubTypes.Type(value = LifecycleCondition.class, name = "lifecycle"),
    @JsonSubTypes.Type(value = FunctionCondition.class, name = "function")
})
public abstract class QueryCondition {

    public static final String GROUP_CONDITION_TYPE = "group";
    public static final String SIMPLE_CONDITION_TYPE = "simple";
    public static final String ARRAY_CONDITION_TYPE = "array"; // this condition type is trino-only
    public static final String LIFECYCLE_CONDITION_TYPE = "lifecycle";
    public static final String FUNCTION_CONDITION_TYPE = "function";

    @JsonProperty("type")
    public abstract String getType();

    @JsonProperty("type")
    public abstract void setType(String type);

}