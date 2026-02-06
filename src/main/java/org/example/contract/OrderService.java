package org.example.contract;

import java.math.BigDecimal;

public interface OrderService {

  BigDecimal checkout();

  void cancel();
}
