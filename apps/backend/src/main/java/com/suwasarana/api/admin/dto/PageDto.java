package com.suwasarana.api.admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** A page of results: {@code {items, page, size, total, totalPages}}. */
public class PageDto<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;
    private final int totalPages;

    public PageDto(List<T> items, int page, int size, long total, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = totalPages;
    }

    public static <S, T> PageDto<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageDto<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotal() { return total; }
    public int getTotalPages() { return totalPages; }
}
