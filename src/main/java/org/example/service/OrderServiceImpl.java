package org.example.service;

import java.math.BigDecimal;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.example.contract.OrderService;
import org.example.entity.Cart;
import org.example.entity.Order;
import org.example.entity.OrderStatus;
import org.example.entity.Product;
import org.example.repository.CartRepository;
import org.example.repository.OrderRepository;
import org.example.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final CartRepository cartRepository;

  @Transactional
  @Override
  public BigDecimal checkout() {
    // 1. Get current cart
    Cart cart = cartRepository
        .findFirstByOrderByIdAsc()
        .orElseThrow(() -> new IllegalStateException("Cart not found"));

    // 2. Check cart is not empty
    if (cart.getProducts() == null || cart.getProducts().isEmpty()) {
      throw new IllegalStateException("Cannot checkout with empty cart");
    }

    // 3. Create new order
    Order order = new Order();
    order.setProducts(new HashSet<>(cart.getProducts()));
    order.setStatus(OrderStatus.OPEN);

    // 4. Calculate total price
    BigDecimal totalPrice = cart.getProducts().stream()
        .map(Product::getPrice)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // 5. Clear cart
    cart.getProducts().clear();
    cartRepository.save(cart);

    // 6. Save order
    orderRepository.save(order);

    return totalPrice;
  }

  @Transactional
  @Override
  public void cancel() {
    // 1. Find the last open order
    Order order = ((Iterable<Order>) orderRepository.findAll())
        .iterator().hasNext()
            ? ((Iterable<Order>) orderRepository.findAll()).iterator().next()
            : null;

    if (order == null) {
      throw new IllegalStateException("No order found to cancel");
    }

    // 2. Check order status is OPEN
    if (order.getStatus() != OrderStatus.OPEN) {
      throw new IllegalStateException("Cannot cancel order with status: " + order.getStatus());
    }

    // 3. Restore stock for each product
    for (Product product : order.getProducts()) {
      Product dbProduct = productRepository
          .findById(product.getId())
          .orElseThrow(
              () -> new IllegalStateException(
                  "Product not found during cancel: " + product.getId()));

      dbProduct.setStock(dbProduct.getStock() + 1);
      productRepository.save(dbProduct);
    }

    // 4. Set order status to CLOSED
    order.setStatus(OrderStatus.CLOSED);
    orderRepository.save(order);
  }
}