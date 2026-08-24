package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
import com.example.banhangtructuyen.application.dto.cart.CartResponse;
import com.example.banhangtructuyen.application.dto.cart.CartViewItemResponse;
import com.example.banhangtructuyen.application.dto.cart.UpdateCartItemQuantityRequest;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.CartService;
import com.example.banhangtructuyen.domain.exception.InsufficientInventoryException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cart service implementation.
 *
 * <p>ATS-14 — Real-time inventory check:
 * <ul>
 *   <li>{@link #addItem}: Validates available inventory via
 *       {@link ProductRepository#findActiveById(Long)} (JOIN FETCH inventory — real DB read).
 *       Throws {@link InsufficientInventoryException} (HTTP 422) if stock is insufficient.</li>
 *   <li>{@link #updateItemQuantity}: Re-fetches current inventory directly from
 *       {@link InventoryRepository#findByProduct_ProductId(Long)} to avoid any stale
 *       entity-cache hit, then validates available stock.</li>
 * </ul>
 *
 * <p>ATS-14 scope: READ-ONLY inventory check. No deduction, no reservation, no order
 * creation is performed here. Those belong to other ATS.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private static final int MAX_QUANTITY = 1000;
    private static final String MIN_QUANTITY_MESSAGE = "Cart item quantity must be at least 1";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCurrentCart(final String keycloakSubject) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        if (cartRepository.findByCustomerId(customer.getCustomerId()).isEmpty()) {
            return emptyCart();
        }

        final List<CartViewItemResponse> items = cartItemRepository.findViewItemsByCustomerId(customer.getCustomerId())
                .stream()
                .map(CartServiceImpl::toViewItemResponse)
                .toList();
        final BigDecimal subtotal = items.stream()
                .map(CartViewItemResponse::itemSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(items, subtotal);
    }

    /**
     * Adds a product to the customer's cart.
     *
     * <p>ATS-14: Inventory check is performed via {@code ProductRepository.findActiveById()}
     * which issues a {@code SELECT PRODUCTS JOIN INVENTORY} query — a real-time read
     * from the database. The check happens BEFORE any cart or cart-item mutation.
     *
     * @throws InsufficientInventoryException (HTTP 422) if requestedQty &gt; availableQty
     * @throws IllegalArgumentException (HTTP 400) if quantity is invalid (≤ 0 or &gt; 1000)
     * @throws ResourceNotFoundException (HTTP 404) if product is not active/found
     */
    @Override
    public CartItemResponse addItem(final String keycloakSubject, final AddCartItemRequest request) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);

        // ATS-14: findActiveById uses JOIN FETCH p.inventory — real-time DB inventory read
        final Product product = productRepository.findActiveById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        // ATS-14: Validate requested quantity against real-time available inventory
        validateQuantityAgainstInventory(request.quantity(), product);

        final Cart cart = findOrCreateCartForUpdate(customer);
        final CartItem existingItem = cartItemRepository.findByCart_CartIdAndProduct_ProductId(
                cart.getCartId(), product.getProductId()).orElse(null);

        final int resultingQuantity = existingItem == null
                ? request.quantity()
                : existingItem.getQuantity() + request.quantity();

        // ATS-14: Validate resulting (accumulated) quantity against real-time inventory
        validateQuantityAgainstInventory(resultingQuantity, product);

        final CartItem item;
        if (existingItem != null) {
            existingItem.setQuantity(resultingQuantity);
            item = existingItem;
        } else {
            item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.quantity())
                    .unitPrice(product.getPrice())
                    .build();
        }

        return toResponse(cartItemRepository.save(item));
    }

    /**
     * Updates the quantity of an existing cart item.
     *
     * <p>ATS-14: Inventory is re-fetched fresh from {@link InventoryRepository#findByProduct_ProductId(Long)}
     * to ensure the current database state is used — not a potentially stale entity loaded
     * from the CartItem → Product relationship. This matches the UpdateCart sequence diagram
     * (step 16: findByProductId, step 17: SELECT INVENTORY WHERE PRODUCT_ID).
     *
     * @throws InsufficientInventoryException (HTTP 422) if requestedQty &gt; availableQty
     * @throws IllegalArgumentException (HTTP 400) if quantity is invalid (≤ 0 or &gt; 1000)
     * @throws ResourceNotFoundException (HTTP 404) if cart item not found or not owned
     */
    @Override
    public CartItemResponse updateItemQuantity(
            final String keycloakSubject,
            final Long cartItemId,
            final UpdateCartItemQuantityRequest request) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final CartItem item = findOwnedCartItemForUpdate(customer.getCustomerId(), cartItemId);

        // ATS-14: Re-fetch inventory fresh from DB — not from the Product entity cache.
        // Matches UpdateCart sequence diagram step 16 (Cache Miss → findByProductId).
        final Long productId = item.getProduct().getProductId();
        final Inventory freshInventory = inventoryRepository.findByProduct_ProductId(productId)
                .orElse(null);

        validateQuantityAgainstFreshInventory(request.quantity(), productId, freshInventory);

        item.setQuantity(request.quantity());
        return toResponse(cartItemRepository.save(item));
    }

    @Override
    public void removeItem(final String keycloakSubject, final Long cartItemId) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final CartItem item = findOwnedCartItemForUpdate(customer.getCustomerId(), cartItemId);

        cartItemRepository.delete(item);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Cart findOrCreateCartForUpdate(final Customer customer) {
        final Long customerId = customer.getCustomerId();
        final Cart existingCart = cartRepository.findByCustomerIdForUpdate(customerId).orElse(null);
        if (existingCart != null) {
            return existingCart;
        }

        customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        return cartRepository.findByCustomerIdForUpdate(customerId)
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .customerId(customerId)
                        .build()));
    }

    private CartItem findOwnedCartItemForUpdate(final Long customerId, final Long cartItemId) {
        cartRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", cartItemId));

        return cartItemRepository.findByCart_CustomerIdAndCartItemId(customerId, cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", cartItemId));
    }

    /**
     * ATS-14 — Validates the requested quantity against inventory loaded with the Product entity.
     * Used in {@link #addItem} where the product (with JOIN FETCH inventory) is already available.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>RULE 3: quantity must be &gt; 0 → {@link IllegalArgumentException}</li>
     *   <li>quantity must not exceed 1000 → {@link IllegalArgumentException}</li>
     *   <li>RULE 2: quantity must not exceed available inventory → {@link InsufficientInventoryException}</li>
     * </ul>
     */
    private static void validateQuantityAgainstInventory(final int requestedQuantity, final Product product) {
        if (requestedQuantity < 1) {
            throw new IllegalArgumentException(MIN_QUANTITY_MESSAGE);
        }
        if (requestedQuantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Cart item quantity must not exceed 1000");
        }
        final int available = product.getInventory() == null
                ? 0
                : product.getInventory().getAvailableQuantity();
        if (requestedQuantity > available) {
            throw new InsufficientInventoryException(product.getProductId(), requestedQuantity, available);
        }
    }

    /**
     * ATS-14 — Validates the requested quantity against a freshly fetched {@link Inventory} object.
     * Used in {@link #updateItemQuantity} where inventory is re-fetched from
     * {@link InventoryRepository} to guarantee a real-time database read.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>RULE 3: quantity must be &gt; 0 → {@link IllegalArgumentException}</li>
     *   <li>quantity must not exceed 1000 → {@link IllegalArgumentException}</li>
     *   <li>RULE 2: quantity must not exceed available inventory → {@link InsufficientInventoryException}</li>
     * </ul>
     */
    private static void validateQuantityAgainstFreshInventory(final int requestedQuantity,
                                                               final Long productId,
                                                               final Inventory freshInventory) {
        if (requestedQuantity < 1) {
            throw new IllegalArgumentException(MIN_QUANTITY_MESSAGE);
        }
        if (requestedQuantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Cart item quantity must not exceed 1000");
        }
        final int available = freshInventory == null ? 0 : freshInventory.getAvailableQuantity();
        if (requestedQuantity > available) {
            throw new InsufficientInventoryException(productId, requestedQuantity, available);
        }
    }

    private static CartItemResponse toResponse(final CartItem item) {
        return new CartItemResponse(
                item.getCartItemId(),
                item.getProduct().getProductId(),
                item.getProduct().getProductName(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }

    private static CartViewItemResponse toViewItemResponse(final CartItem item) {
        final BigDecimal itemSubtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartViewItemResponse(
                item.getCartItemId(),
                item.getProduct().getProductId(),
                item.getProduct().getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                itemSubtotal
        );
    }

    private static CartResponse emptyCart() {
        return new CartResponse(List.of(), BigDecimal.ZERO);
    }
}
