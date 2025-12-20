package com.java_template.application.controller;

/**
 * DTO for search requests to the /items/search endpoint.
 * Encapsulates search parameters: query, type filter, and pagination settings.
 */
public class SearchRequest {

    private String q;
    private String type;
    private Integer limit;
    private Integer offset;

    /**
     * No-arg constructor for deserialization.
     */
    public SearchRequest() {
    }

    /**
     * Constructor with all parameters.
     */
    public SearchRequest(String q, String type, Integer limit, Integer offset) {
        this.q = q;
        this.type = type;
        this.limit = limit;
        this.offset = offset;
    }

    /**
     * Get the search query.
     *
     * @return the search query string
     */
    public String getQ() {
        return q;
    }

    /**
     * Set the search query.
     *
     * @param q the search query string
     */
    public void setQ(String q) {
        this.q = q;
    }

    /**
     * Get the entity type filter.
     *
     * @return the entity type (e.g., "story", "comment")
     */
    public String getType() {
        return type;
    }

    /**
     * Set the entity type filter.
     *
     * @param type the entity type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the result limit with default fallback.
     * Returns 20 if limit is null.
     *
     * @return the limit value or 20 if null
     */
    public int getLimit() {
        return limit != null ? limit : 20;
    }

    /**
     * Set the result limit.
     *
     * @param limit the maximum number of results
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
     * Get the pagination offset with default fallback.
     * Returns 0 if offset is null.
     *
     * @return the offset value or 0 if null
     */
    public int getOffset() {
        return offset != null ? offset : 0;
    }

    /**
     * Set the pagination offset.
     *
     * @param offset the pagination offset
     */
    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
                "q='" + q + '\'' +
                ", type='" + type + '\'' +
                ", limit=" + limit +
                ", offset=" + offset +
                '}';
    }
}

