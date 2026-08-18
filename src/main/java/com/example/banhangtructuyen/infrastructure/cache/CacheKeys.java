package com.example.banhangtructuyen.infrastructure.cache;

import java.math.BigDecimal;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String productList(final int page, final int size,
                                      final Long categoryId, final String search,
                                      final BigDecimal minPrice, final BigDecimal maxPrice) {
        return "product:list:" + page + ":" + size
                + ":" + (categoryId == null ? "" : categoryId)
                + ":" + (search == null ? "" : search)
                + ":" + (minPrice == null ? "" : minPrice.toPlainString())
                + ":" + (maxPrice == null ? "" : maxPrice.toPlainString());
    }

    public static String productDetail(final Long productId) {
        return "product:detail:" + productId;
    }
}
