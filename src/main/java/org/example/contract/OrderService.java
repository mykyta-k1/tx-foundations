package org.example.contract;

import java.math.BigDecimal;
import java.util.UUID;
import org.example.entity.Product;

public interface OrderService {

  BigDecimal checkout();

  void cancel();

}