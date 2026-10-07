package com.luminous.api.common;

import org.springframework.http.HttpStatus;

/** 在服务层统一校验，限制单页、深度及整数溢出，不依赖 Controller 调用方式。 */
public final class PaginationPolicy {
    public static final int MAX_SIZE = 100;
    public static final int POST_WINDOW = 500;
    public static final int MAX_OFFSET = 10000;

    private PaginationPolicy() {}

    public static long validate(long page, long size) {
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw invalid();
        }
        // 先除法比较，避免巨大 page 与 size 相乘发生溢出。
        if (page - 1 > MAX_OFFSET / size) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAGE_DEPTH_LIMIT", "分页深度超过一万条，请缩小筛选范围");
        }
        return (page - 1) * size;
    }

    public static long posts(long page, long size) {
        long offset = validate(page, size);
        if (offset >= POST_WINDOW) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "POST_WINDOW_LIMIT",
                    "当前筛选最多浏览前五百条，请调整社区、作者或搜索条件");
        }
        return offset;
    }

    public static void offset(int offset) {
        if (offset < 0 || offset > MAX_OFFSET) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                "page 必须为正数，size 为一至一百，offset 为零至一万");
    }
}
