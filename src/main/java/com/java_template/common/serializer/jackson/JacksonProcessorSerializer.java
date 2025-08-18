package com.java_template.common.serializer.jackson;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerEnum;

public class JacksonProcessorSerializer implements ProcessorSerializer {
    @Override
    public String getType() {
        return SerializerEnum.JACKSON.getType();
    }
}
