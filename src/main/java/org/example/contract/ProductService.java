package org.example.contract;

import java.util.UUID;
import org.example.entity.Product;

public interface ProductService {

  Product addToCart(UUID productId);
}