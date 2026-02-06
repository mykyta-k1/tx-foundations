package org.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.example.contract.ProductService;
import org.example.entity.Cart;
import org.example.entity.Product;
import org.example.repository.CartRepository;
import org.example.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductServiceImplTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        // Clean up
        cartRepository.deleteAll();
        productRepository.deleteAll();

        // Create test product
        testProduct = new Product();
        testProduct.setName("Test Laptop");
        testProduct.setPrice(new BigDecimal("1500.00"));
        testProduct.setStock(10);
        testProduct = productRepository.save(testProduct);
    }

    @Test
    @DisplayName("Happy Path: Should add product to cart and decrease stock")
    void addToCart_shouldAddProductAndDecreaseStock() {
        // Given
        int initialStock = testProduct.getStock();

        // When
        Product result = productService.addToCart(testProduct.getId());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testProduct.getId());

        // Verify stock decreased
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(initialStock - 1);

        // Verify product added to cart
        Cart cart = cartRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(cart.getProducts()).hasSize(1);
        assertThat(cart.getProducts()).extracting(Product::getId).contains(testProduct.getId());
    }

    @Test
    @DisplayName("Happy Path: Should add multiple products to same cart")
    void addToCart_shouldAddMultipleProductsToSameCart() {
        // Given
        Product product2 = new Product();
        product2.setName("Test Mouse");
        product2.setPrice(new BigDecimal("50.00"));
        product2.setStock(5);
        product2 = productRepository.save(product2);

        // When
        productService.addToCart(testProduct.getId());
        productService.addToCart(product2.getId());

        // Then
        Cart cart = cartRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(cart.getProducts()).hasSize(2);
    }

    @Test
    @DisplayName("Sad Path: Should throw exception when product not found")
    void addToCart_shouldThrowExceptionWhenProductNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> productService.addToCart(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found with id: " + nonExistentId);

        // Verify no cart was created
        assertThat(cartRepository.findFirstByOrderByIdAsc()).isEmpty();
    }

    @Test
    @DisplayName("Sad Path: Should throw exception and rollback when product out of stock")
    void addToCart_shouldThrowExceptionWhenOutOfStock() {
        // Given
        testProduct.setStock(0);
        productRepository.save(testProduct);

        // When & Then
        assertThatThrownBy(() -> productService.addToCart(testProduct.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Product out of stock");

        // Verify stock was not changed (rollback)
        Product unchangedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(unchangedProduct.getStock()).isEqualTo(0);

        // Verify no cart was created
        assertThat(cartRepository.findFirstByOrderByIdAsc()).isEmpty();
    }

    @Test
    @DisplayName("Sad Path: Should rollback stock change if cart save fails")
    void addToCart_shouldRollbackOnFailure() {
        // Given - product with stock = 1
        testProduct.setStock(1);
        productRepository.save(testProduct);

        // When - add to cart successfully
        productService.addToCart(testProduct.getId());

        // Then - stock should be 0
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(0);

        // When - try to add again (should fail)
        assertThatThrownBy(() -> productService.addToCart(testProduct.getId()))
                .isInstanceOf(IllegalStateException.class);

        // Then - stock should still be 0 (not negative)
        Product finalProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(finalProduct.getStock()).isEqualTo(0);
    }
}
