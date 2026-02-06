package org.example.repository;

import java.math.BigDecimal;
import java.util.UUID;
import org.example.entity.Order;
import org.springframework.data.repository.CrudRepository;

public interface OrderRepository extends CrudRepository<Order, UUID> {

}