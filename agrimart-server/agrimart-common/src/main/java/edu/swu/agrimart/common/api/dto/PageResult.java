package edu.swu.agrimart.common.api.dto;

import java.util.List;

/**
 * 页码从 1 开始的统一列表响应。
 * @param records 当前页记录
 * @param total 满足条件的记录总数
 * @param page 当前页码
 * @param size 每页记录上限
 * @param totalPages 总页数，空列表为 0
 * @param <T> 记录类型
 */
public record PageResult<T>(List<T> records, long total, int page, int size, long totalPages) {}
