package org.cyoda.cloud.api.event.common.statemachine.conf;

import com.fasterxml.jackson.annotation.*;

/**
 * Base class for the generated processor definitions.
 *<p>
 * jsonschema2pojo doesn't support the generation of discriminator-related annotations, so this is the workaround
 * and we just using 'existingJavaType' in json-schema to declare inheritance.
 */
@SuppressWarnings({"LombokGetterMayBeUsed", "LombokSetterMayBeUsed"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "name"
})
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type", // when null, we should use ExternalizedProcessorDefinition
        defaultImpl = ExternalizedProcessorDefinition.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ExternalizedProcessorDefinition.class, name = ProcessorDefinitionConstants.EXTERNALIZED_PROCESSOR_TYPE),
        @JsonSubTypes.Type(value = ScheduledTransitionProcessorDefinition.class, name = ProcessorDefinitionConstants.SCHEDULED_PROCESSOR_TYPE)
})
public abstract class ProcessorDefinition {

    @JsonProperty("type")
    public String getType() {
        return null;
    }

    @JsonProperty("type")
    public void setType(String type) {
        // do nothing by default
    }

    @JsonProperty("name")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
