package com.hackerrank.challenge.infrastructure.persistence;

import com.hackerrank.challenge.domain.entity.Seller;
import com.hackerrank.challenge.domain.repository.SellerRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store en memoria para {@link Seller}. Solo lo necesario para cargar el seed.
 */
@Repository
public class InMemorySellerRepository implements SellerRepository {

  private final Map<UUID, Seller> store = new ConcurrentHashMap<>();

  @Override
  public Seller save(Seller seller) {
    store.put(seller.getId(), seller);
    return seller;
  }

  @Override
  public Optional<Seller> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Seller> findAll() {
    return List.copyOf(store.values());
  }
}
