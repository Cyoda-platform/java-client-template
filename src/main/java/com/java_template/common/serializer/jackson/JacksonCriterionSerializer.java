package com.java_template.common.serializer.jackson;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerEnum;

public class JacksonCriterionSerializer implements CriterionSerializer {
    @Override
    public String getType() {
        return SerializerEnum.JACKSON.getType();
    }
}
