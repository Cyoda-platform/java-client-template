package com.java_template.common.serializer;

public class SerializerFactory {
    public static ProcessorSerializer getProcessorSerializer() {
        return getDefaultProcessorSerializer(SerializerEnum.JACKSON.getType());
    }

    public static CriterionSerializer getCriteriaSerializer() {
        return getDefaultCriteriaSerializer(SerializerEnum.JACKSON.getType());
    }

    private static ProcessorSerializer getDefaultProcessorSerializer(String type) {
        // Simplified implementation for compilation
        return new com.java_template.common.serializer.jackson.JacksonProcessorSerializer();
    }

    private static CriterionSerializer getDefaultCriteriaSerializer(String type) {
        // Simplified implementation for compilation
        return new com.java_template.common.serializer.jackson.JacksonCriterionSerializer();
    }
}
