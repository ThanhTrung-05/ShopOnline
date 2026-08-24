package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
import com.example.banhangtructuyen.application.dto.cart.CartResponse;
import com.example.banhangtructuyen.application.dto.cart.CartViewItemResponse;
import com.example.banhangtructuyen.application.dto.cart.UpdateCartItemQuantityRequest;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.CartService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private static final int MAX_QUANTITY = 1000;
    private static final String MIN_QUANTITY_MESSAGE = "Cart item quantity must be at least 1";
    private static final String INSUFFICIENT_STOCK_MESSAGE = "Requested quantity exceeds available stock";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
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

    @Override
    public CartItemResponse addItem(final String keycloakSubject, final AddCartItemRequest request) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final Product product = productRepository.findActiveById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        validateQuantity(request.quantity(), product);

        final Cart cart = findOrCreateCartForUpdate(customer);
        final CartItem existingItem = cartItemRepository.findByCart_CartIdAndProduct_ProductId(
                cart.getCartId(), product.getProductId()).orElse(null);

        final int resultingQuantity = existingItem == null
                ? request.quantity()
                : existingItem.getQuantity() + request.quantity();
        validateQuantity(resultingQuantity, product);

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

    @Override
    public CartItemResponse updateItemQuantity(
            final String keycloakSubject,
            final Long cartItemId,
            final UpdateCartItemQuantityRequest request) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final CartItem item = findOwnedCartItemForUpdate(customer.getCustomerId(), cartItemId);

        validateQuantity(request.quantity(), item.getProduct());
        item.setQuantity(request.quantity());
        return toResponse(cartItemRepository.save(item));
    }

    @Override
    public void removeItem(final String keycloakSubject, final Long cartItemId) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final CartItem item = findOwnedCartItemForUpdate(customer.getCustomerId(), cartItemId);

        cartItemRepository.delete(item);
    }

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

    private static void validateQuantity(final int requestedQuantity, final Product product) {
        if (requestedQuantity < 1) {
            throw new IllegalArgumentException(MIN_QUANTITY_MESSAGE);
        }
        if (requestedQuantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Cart item quantity must not exceed 1000");
        }
        if (requestedQuantity > availableStock(product)) {
            throw new IllegalArgumentException(INSUFFICIENT_STOCK_MESSAGE);
        }
    }

    private static int availableStock(final Product product) {
        return product.getInventory() == null
                ? 0
                : product.getInventory().getAvailableQuantity();
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
