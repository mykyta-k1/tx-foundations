package org.example.repository;

import java.util.Optional;
import java.util.UUID;
import org.example.entity.Cart;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CartRepository extends CrudRepository<Cart, UUID> {

  @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.products")
  Optional<Cart> findFirstByOrderByIdAsc();
}
