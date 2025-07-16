package com.java_template.application.entity.pet;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@Data
public class Pet {
    private UUID technicalId;
    private String name;
    private String type;
    private String status;
    private List<String> tags = new ArrayList<>();
}
