package com.blog.aispringboot.common;

import java.util.List;

/**
 * 分页响应体。
 *
 * <p>不用 Spring Data 的 {@code Page} 直接返回：它序列化后带一堆前端用不到的字段
 * （{@code pageable}、{@code sort}、{@code first}…），且不同 Spring Data 版本字段还会变。
 * 前端只依赖 {@code records} 与 {@code total}。
 */
public record PageResult<T>(List<T> records, long total, long current, long size) {

    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements(),
                page.getNumber() + 1L, page.getSize());
    }
}
