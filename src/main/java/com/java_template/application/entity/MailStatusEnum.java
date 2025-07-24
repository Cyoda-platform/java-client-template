package com.java_template.application.entity;

public enum MailStatusEnum {
    CREATED,
    PROCESSING,
    SENT_HAPPY,
    SENT_GLOOMY,
    FAILED;

    public String name() {
        return this.toString();
    }
}
