package com.cyoda.app.service;

public interface EmailService {
    void sendEmail(String to, String subject, String body) throws Exception;

    class BounceException extends Exception {
        public BounceException(String message) {
            super(message);
        }
    }
}
