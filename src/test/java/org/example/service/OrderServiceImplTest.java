package org.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashSet;
import org.example.contract.OrderService;
import org.example.entity.Cart;
import org.example.entity.Order;
import org.example.entity.OrderStatus;
import org.example.entity.Product;
import org.example.repository.CartRepository;
import org.example.repository.OrderRepository;
import org.example.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderServiceImplTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    private Product product1;
    private Product product2;
    private Cart cart;

    @BeforeEach
    void setUp() {
        // Clean up
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();

        // Create test products
        product1 = new Product();
        product1.setName("Laptop");
        product1.setPrice(new BigDecimal("1200.00"));
        product1.setStock(5);
        product1 = productRepository.save(product1);

        product2 = new Product();
        product2.setName("Mouse");
        product2.setPrice(new BigDecimal("25.50"));
        product2.setStock(10);
        product2 = productRepository.save(product2);

        // Create cart with products
        cart = new Cart();
        cart.setProducts(new HashSet<>());
        cart.getProducts().add(product1);
        cart.getProducts().add(product2);
        cart = cartRepository.save(cart);
    }

    // ========== CHECKOUT TESTS ==========

    @Test
    @DisplayName("Checkout Happy Path: Should create order and calculate total price")
    void checkout_shouldCreateOrderAndCalculateTotal() {
        // When
        BigDecimal total = orderService.checkout();

        // Then
        assertThat(total).isEqualByComparingTo(new BigDecimal("1225.50")); // 1200 + 25.50

        // Verify order was created
        Order order = ((Iterable<Order>) orderRepository.findAll()).iterator().next();
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(order.getProducts()).hasSize(2);

        // Verify cart was cleared
        Cart updatedCart = cartRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(updatedCart.getProducts()).isEmpty();
    }

    @Test
    @DisplayName("Checkout Happy Path: Should handle single product")
    void checkout_shouldHandleSingleProduct() {
        // Given - cart with only one product
        cart.getProducts().clear();
        cart.getProducts().add(product1);
        cartRepository.save(cart);

        // When
        BigDecimal total = orderService.checkout();

        // Then
        assertThat(total).isEqualByComparingTo(new BigDecimal("1200.00"));
    }

    @Test
    @DisplayName("Checkout Sad Path: Should throw exception when cart is empty")
    void checkout_shouldThrowExceptionWhenCartEmpty() {
        // Given - empty cart
        cart.getProducts().clear();
        cartRepository.save(cart);

        // When & Then
        assertThatThrownBy(() -> orderService.checkout())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot checkout with empty cart");

        // Verify no order was created
        assertThat(orderRepository.findAll()).hasSize(0);
    }

    @Test
    @DisplayName("Checkout Sad Path: Should throw exception when cart not found")
    void checkout_shouldThrowExceptionWhenCartNotFound() {
        // Given - no cart
        cartRepository.deleteAll();

        // When & Then
        assertThatThrownBy(() -> orderService.checkout())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cart not found");
    }

    // ========== CANCEL TESTS ==========

    @Test
    @DisplayName("Cancel Happy Path: Should cancel order and restore stock")
    void cancel_shouldCancelOrderAndRestoreStock() {
        // Given - create an order first
        int initialStock1 = product1.getStock();
        int initialStock2 = product2.getStock();

        Order order = new Order();
        order.setStatus(OrderStatus.OPEN);
        order.setProducts(new HashSet<>());
        order.getProducts().add(product1);
        order.getProducts().add(product2);
        orderRepository.save(order);

        // When
        orderService.cancel();

        // Then
        // Verify order status changed to CLOSED
        Order cancelledOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CLOSED);

        // Verify stock was restored
        Product restoredProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        Product restoredProduct2 = productRepository.findById(product2.getId()).orElseThrow();
        assertThat(restoredProduct1.getStock()).isEqualTo(initialStock1 + 1);
        assertThat(restoredProduct2.getStock()).isEqualTo(initialStock2 + 1);
    }

    @Test
    @DisplayName("Cancel Happy Path: Should restore stock for multiple products")
    void cancel_shouldRestoreStockForMultipleProducts() {
        // Given
        product1.setStock(0); // Out of stock
        productRepository.save(product1);

        Order order = new Order();
        order.setStatus(OrderStatus.OPEN);
        order.setProducts(new HashSet<>());
        order.getProducts().add(product1);
        orderRepository.save(order);

        // When
        orderService.cancel();

        // Then - stock should be restored to 1
        Product restoredProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(restoredProduct.getStock()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cancel Sad Path: Should throw exception when no order found")
    void cancel_shouldThrowExceptionWhenNoOrderFound() {
        // Given - no orders
        orderRepository.deleteAll();

        // When & Then
        assertThatThrownBy(() -> orderService.cancel())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No order found to cancel");
    }

    @Test
    @DisplayName("Cancel Sad Path: Should throw exception when order already closed")
    void cancel_shouldThrowExceptionWhenOrderAlreadyClosed() {
        // Given - closed order
        Order order = new Order();
        order.setStatus(OrderStatus.CLOSED);
        order.setProducts(new HashSet<>());
        order.getProducts().add(product1);
        orderRepository.save(order);

        int initialStock = product1.getStock();

        // When & Then
        assertThatThrownBy(() -> orderService.cancel())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel order with status: CLOSED");

        // Verify stock was NOT changed (rollback)
        Product unchangedProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(unchangedProduct.getStock()).isEqualTo(initialStock);
    }
}
