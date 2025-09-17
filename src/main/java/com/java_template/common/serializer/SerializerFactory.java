package com.java_template.common.serializer;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ABOUTME: Factory component for creating and managing ProcessorSerializer and CriterionSerializer
 * instances with caching and type-based serializer selection.
 */
@Component
public class SerializerFactory {

    private final ConcurrentMap<String, ProcessorSerializer> processorSerializers;
    private final ConcurrentMap<String, CriterionSerializer> criterionSerializers;

    public SerializerFactory(
            List<ProcessorSerializer> processorSerializerList,
            List<CriterionSerializer> criterionSerializerList) {

        // Index processor serializers by type
        this.processorSerializers = new ConcurrentHashMap<>();
        for (ProcessorSerializer serializer : processorSerializerList) {
            this.processorSerializers.put(serializer.getType(), serializer);
        }

        // Index criterion serializers by type
        this.criterionSerializers = new ConcurrentHashMap<>();
        for (CriterionSerializer serializer : criterionSerializerList) {
            this.criterionSerializers.put(serializer.getType(), serializer);
        }
    }

    /**
     * Gets a ProcessorSerializer by type.
     *
     * @param type the serializer type (e.g., "jackson")
     * @return ProcessorSerializer instance
     * @throws IllegalArgumentException if no serializer of the specified type is found
     */
    public ProcessorSerializer getDefaultProcessorSerializer(String type) {
        ProcessorSerializer serializer = processorSerializers.get(type);
        if (serializer == null) {
            throw new IllegalArgumentException("No ProcessorSerializer of type '" + type + "' found");
        }
        return serializer;
    }

    /**
     * Gets the default ProcessorSerializer (jackson).
     *
     * @return ProcessorSerializer instance
     * @throws IllegalStateException if no jackson ProcessorSerializer is available
     */
    public ProcessorSerializer getDefaultProcessorSerializer() {
        return getDefaultProcessorSerializer(SerializerEnum.JACKSON.getType());
    }

    /**
     * Gets a CriterionSerializer by type.
     *
     * @param type the serializer type (e.g., "jackson")
     * @return CriterionSerializer instance
     * @throws IllegalArgumentException if no serializer of the specified type is found
     */
    public CriterionSerializer getDefaultCriteriaSerializer(String type) {
        CriterionSerializer serializer = criterionSerializers.get(type);
        if (serializer == null) {
            throw new IllegalArgumentException("No CriterionSerializer of type '" + type + "' found");
        }
        return serializer;
    }

    /**
     * Gets the default CriterionSerializer (jackson).
     *
     * @return CriterionSerializer instance
     * @throws IllegalStateException if no jackson CriterionSerializer is available
     */
    public CriterionSerializer getDefaultCriteriaSerializer() {
        return getDefaultCriteriaSerializer(SerializerEnum.JACKSON.getType());
    }

}