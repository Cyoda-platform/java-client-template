package com.java_template.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Pagination wrapper for repository search results containing data and pagination metadata.
 * Provides total count and calculated total pages information for paginated queries.
 *
 * @param <T> The type of data in the page
 */
public record PageResult<T>(
        @JsonProperty("searchId") UUID searchId,
        @JsonProperty("data") List<T> data,
        @JsonProperty("pageNumber") int pageNumber,
        @JsonProperty("pageSize") int pageSize,
        @JsonProperty("totalElements") long totalElements,
        @JsonProperty("totalPages") int totalPages
) {
    /**
     * Creates a PageResult from search results with pagination metadata.
     *
     * @param searchId The unique identifier for this search (used to retrieve subsequent pages).
     *                 Null for in-memory searches that don't support pagination.
     * @param data The list of items in this page
     * @param pageNumber The current page number (0-based)
     * @param pageSize The size of each page
     * @param totalElements The total number of elements across all pages
     * @param <T> The type of data in the page
     * @return A new PageResult instance
     */
    public static <T> PageResult<T> of(UUID searchId, List<T> data, int pageNumber, int pageSize, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new PageResult<>(searchId, data, pageNumber, pageSize, totalElements, totalPages);
    }

    /**
     * Checks if this is the first page.
     *
     * @return true if this is page 0
     */
    public boolean isFirst() {
        return pageNumber == 0;
    }

    /**
     * Checks if this is the last page.
     *
     * @return true if this is the last page
     */
    public boolean isLast() {
        return pageNumber >= totalPages - 1;
    }

    /**
     * Checks if there is a next page.
     *
     * @return true if there is a next page
     */
    public boolean hasNext() {
        return pageNumber < totalPages - 1;
    }

    /**
     * Checks if there is a previous page.
     *
     * @return true if there is a previous page
     */
    public boolean hasPrevious() {
        return pageNumber > 0;
    }
}

