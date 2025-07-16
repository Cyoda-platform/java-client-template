package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(Controller.class);

    public Controller(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        logger.info("Controller initialized");
    }

    // Add controller methods here as needed

}