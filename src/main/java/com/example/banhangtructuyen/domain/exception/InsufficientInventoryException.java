package com.example.banhangtructuyen.domain.exception;

/**
 * ATS-14 — Thrown when the requested quantity exceeds the available inventory
 * for a product at the time of the add-to-cart or update-cart-quantity operation.
 *
 * <p>Available inventory = QUANTITY - RESERVED_QUANTITY (from the INVENTORY table).
 * This exception maps to HTTP 422 Unprocessable Entity per the AddToCart and
 * UpdateCart sequence diagrams.
 *
 * <p>This exception DOES NOT trigger any inventory deduction, reservation, or
 * order creation — it is a pure read-and-reject check (ATS-14 scope only).
 */
public class InsufficientInventoryException extends RuntimeException {

    private final Long productId;
    private final int requested;
    private final int available;

    /**
     * Creates an {@code InsufficientInventoryException} for the given product.
     *
     * @param productId the ID of the product whose inventory is insufficient
     * @param requested the quantity the customer requested
     * @param available the quantity actually available (quantity - reservedQuantity)
     */
    public InsufficientInventoryException(final Long productId,
                                          final int requested,
                                          final int available) {
        super(String.format(
                "Không đủ hàng tồn kho cho sản phẩm %d: yêu cầu %d, còn lại %d.",
                productId, requested, available));
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    /** @return the product ID that triggered this exception */
    public Long getProductId() {
        return productId;
    }

    /** @return the quantity that was requested */
    public int getRequested() {
        return requested;
    }

    /** @return the quantity that was available at the time of the check */
    public int getAvailable() {
        return available;
    }
}
