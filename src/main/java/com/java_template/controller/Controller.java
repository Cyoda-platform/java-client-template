package com.example.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final ObjectMapper objectMapper;

    // ObjectMapper is injected via constructor
    public Controller(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        logger.info("Controller initialized with injected ObjectMapper");
    }

    // Add controller methods here, using logger.info(...) or logger.error(...) as needed

}