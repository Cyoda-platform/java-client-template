package com.cyoda.app.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "subscribers")
public class Subscriber {

    @Id
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "opt_in_timestamp")
    private OffsetDateTime optInTimestamp;

    @Column(nullable = false)
    private boolean unsubscribed = false;

    @Column(name = "unsubscribe_timestamp")
    private OffsetDateTime unsubscribeTimestamp;

    public Subscriber() {
    }

    public Subscriber(String email, OffsetDateTime optInTimestamp, boolean unsubscribed, OffsetDateTime unsubscribeTimestamp) {
        this.email = email;
        this.optInTimestamp = optInTimestamp;
        this.unsubscribed = unsubscribed;
        this.unsubscribeTimestamp = unsubscribeTimestamp;
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

    public boolean isUnsubscribed() {
        return unsubscribed;
    }

    public void setUnsubscribed(boolean unsubscribed) {
        this.unsubscribed = unsubscribed;
    }

    public OffsetDateTime getUnsubscribeTimestamp() {
        return unsubscribeTimestamp;
    }

    public void setUnsubscribeTimestamp(OffsetDateTime unsubscribeTimestamp) {
        this.unsubscribeTimestamp = unsubscribeTimestamp;
    }
}
