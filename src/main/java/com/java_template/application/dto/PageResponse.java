package com.java_template.application.dto;

import lombok.Data;

import java.util.List;

/**
 * Simple pagination response wrapper
 */
@Data
public class PageResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number;

    public PageResponse(List<T> content, int page, int size, long totalElements) {
        this.content = content;
        this.number = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = (int) Math.ceil((double) totalElements / size);
    }
}
