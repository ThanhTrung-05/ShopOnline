package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
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

@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private static final int MAX_QUANTITY = 1000;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Override
    public CartItemResponse addItem(final String keycloakSubject, final AddCartItemRequest request) {
        final Customer customer = customerRepository.findByKeycloakUserId(keycloakSubject)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", keycloakSubject));
        final Product product = productRepository.findActiveById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        final Cart cart = cartRepository.findByCustomerId(customer.getCustomerId())
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .customerId(customer.getCustomerId())
                        .build()));

        final CartItem item = cartItemRepository.findByCart_CartIdAndProduct_ProductId(
                        cart.getCartId(), product.getProductId())
                .map(existing -> incrementQuantity(existing, request.quantity()))
                .orElseGet(() -> CartItem.builder()
                        .cart(cart)
                        .product(product)
                        .quantity(request.quantity())
                        .unitPrice(product.getPrice())
                        .build());

        return toResponse(cartItemRepository.save(item));
    }

    private static CartItem incrementQuantity(final CartItem item, final int quantityToAdd) {
        final int resultingQuantity = item.getQuantity() + quantityToAdd;
        if (resultingQuantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Cart item quantity must not exceed 1000");
        }
        item.setQuantity(resultingQuantity);
        return item;
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
}
