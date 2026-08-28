package com.example.banhangtructuyen.domain.exception;

/**
 * Thrown during order placement when a product's available stock
 * (QUANTITY - RESERVED_QUANTITY) is less than the requested quantity.
 * Triggers HTTP 409 Conflict in GlobalExceptionHandler.
 */
public class InsufficientStockException extends RuntimeException {

    private final String productName;
    private final int requested;
    private final int available;

    public InsufficientStockException(
            final String productName, final int requested, final int available) {
        super("Sản phẩm \"" + productName + "\" không đủ tồn kho: "
                + "yêu cầu " + requested + ", còn lại " + available);
        this.productName = productName;
        this.requested  = requested;
        this.available  = available;
    }

    public String getProductName() { return productName; }
    public int getRequested()      { return requested; }
    public int getAvailable()      { return available; }
}
