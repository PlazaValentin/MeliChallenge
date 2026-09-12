package com.hackerrank.challenge.infrastructure.persistence;

import com.hackerrank.challenge.domain.entity.Product;
import com.hackerrank.challenge.domain.repository.ProductRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store en memoria para {@link Product}. Solo lo necesario para cargar el seed.
 */
@Repository
public class InMemoryProductRepository implements ProductRepository {

  private final Map<UUID, Product> store = new ConcurrentHashMap<>();

  @Override
  public Product save(Product product) {
    store.put(product.getId(), product);
    return product;
  }

  @Override
  public Optional<Product> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Product> findAll() {
    return List.copyOf(store.values());
  }
}
