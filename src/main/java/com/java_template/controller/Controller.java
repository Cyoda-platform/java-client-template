package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final ObjectMapper objectMapper;

    // Constructor injection of ObjectMapper
    public Controller(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Add your controller methods here, using logger for logging

}