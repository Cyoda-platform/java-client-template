package com.cyoda.app.dto;

import java.time.OffsetDateTime;

public class SubscriberDto {
    private String email;
    private OffsetDateTime optInTimestamp;

    public SubscriberDto() {
    }

    public SubscriberDto(String email, OffsetDateTime optInTimestamp) {
        this.email = email;
        this.optInTimestamp = optInTimestamp;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public OffsetDateTime getOptInTimestamp() {
        return optInTimestamp;
    }

    public void setOptInTimestamp(OffsetDateTime optInTimestamp) {
        this.optInTimestamp = optInTimestamp;
    }
}