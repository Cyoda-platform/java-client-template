package com.cyoda.app.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cat_facts")
public class CatFact {

    @Id
    @Column(nullable = false, unique = true)
    private String id;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "fetched_timestamp")
    private OffsetDateTime fetchedTimestamp;

    public CatFact() {
    }

    public CatFact(String id, String text, OffsetDateTime fetchedTimestamp) {
        this.id = id;
        this.text = text;
        this.fetchedTimestamp = fetchedTimestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public OffsetDateTime getFetchedTimestamp() {
        return fetchedTimestamp;
    }

    public void setFetchedTimestamp(OffsetDateTime fetchedTimestamp) {
        this.fetchedTimestamp = fetchedTimestamp;
    }
}
