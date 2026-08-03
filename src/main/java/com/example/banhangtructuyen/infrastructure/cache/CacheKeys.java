package com.example.banhangtructuyen.infrastructure.cache;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String productList(final int page, final int size,
                                      final String categoryCode, final String search) {
        return "product:list:" + page + ":" + size
                + ":" + (categoryCode == null ? "" : categoryCode)
                + ":" + (search == null ? "" : search);
    }

    public static String productDetail(final Long productId) {
        return "product:detail:" + productId;
    }
}
