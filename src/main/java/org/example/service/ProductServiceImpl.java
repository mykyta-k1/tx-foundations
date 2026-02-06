package org.example.service;

import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.contract.ProductService;
import org.example.entity.Cart;
import org.example.entity.Product;
import org.example.repository.CartRepository;
import org.example.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

  private final ProductRepository productRepository;
  private final CartRepository cartRepository;

  @Transactional
  @Override
  public Product addToCart(UUID productId) {
    // 1. Find product by ID
    Product product = productRepository
        .findById(productId)
        .orElseThrow(
            () -> new IllegalArgumentException("Product not found with id: " + productId));

    // 2. Check stock availability
    if (product.getStock() <= 0) {
      throw new IllegalStateException("Product out of stock: " + product.getName());
    }

    // 3. Decrease stock
    product.setStock(product.getStock() - 1);
    productRepository.save(product);

    // 4. Get or create cart (singleton pattern)
    Cart cart = cartRepository
        .findFirstByOrderByIdAsc()
        .orElseGet(
            () -> {
              Cart newCart = new Cart();
              newCart.setProducts(new HashSet<>());
              return cartRepository.save(newCart);
            });

    // 5. Add product to cart
    cart.getProducts().add(product);
    cartRepository.save(cart);

    return product;
  }
}