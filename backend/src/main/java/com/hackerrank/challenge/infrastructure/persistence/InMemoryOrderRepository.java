package com.hackerrank.challenge.infrastructure.persistence;

import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.repository.OrderRepository;
import com.hackerrank.challenge.domain.repository.OrderSearchCriteria;
import org.springframework.stereotype.Repository;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store principal en memoria para {@link Order} (no un cache: no hay eviction,
 * ver DECISIONS.md). {@link ConcurrentHashMap} alcanza para thread-safety en
 * save/findById; el filtrado de {@link #findBySeller} es un recorrido de solo
 * lectura sobre una vista inmutable de los valores, sin necesidad de bloqueo
 * adicional.
 */
@Repository
public class InMemoryOrderRepository implements OrderRepository {

  private final Map<UUID, Order> store = new ConcurrentHashMap<>();

  @Override
  public Order save(Order order) {
    store.put(order.getId(), order);
    return order;
  }

  @Override
  public Optional<Order> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Order> findBySeller(UUID sellerId, OrderSearchCriteria criteria) {
    OrderSearchCriteria effectiveCriteria = criteria == null ? OrderSearchCriteria.none() : criteria;
    return store.values().stream()
        .filter(order -> order.belongsTo(sellerId))
        .filter(order -> matchesStatus(order, effectiveCriteria))
        .filter(order -> matchesCreatedFrom(order, effectiveCriteria))
        .filter(order -> matchesCreatedBefore(order, effectiveCriteria))
        .filter(order -> matchesBuyerText(order, effectiveCriteria))
        .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
        .toList();
  }

  private boolean matchesStatus(Order order, OrderSearchCriteria criteria) {
    return criteria.statusFilter()
        .map(statuses -> statuses.contains(order.getStatus()))
        .orElse(true);
  }

  private boolean matchesCreatedFrom(Order order, OrderSearchCriteria criteria) {
    return criteria.createdFromFilter()
        .map(from -> !order.getCreatedAt().isBefore(from))
        .orElse(true);
  }

  private boolean matchesCreatedBefore(Order order, OrderSearchCriteria criteria) {
    return criteria.createdBeforeFilter()
        .map(before -> order.getCreatedAt().isBefore(before))
        .orElse(true);
  }

  private boolean matchesBuyerText(Order order, OrderSearchCriteria criteria) {
    return criteria.buyerTextFilter()
        .map(text -> {
          String needle = normalize(text);
          return normalize(order.getBuyer().name()).contains(needle)
              || normalize(order.getBuyer().email()).contains(needle);
        })
        .orElse(true);
  }

  /**
   * Sin acentos y en minúsculas, para el matching case-insensitive del filtro.
   */
  private static String normalize(String value) {
    String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
    return withoutAccents.toLowerCase(Locale.ROOT);
  }
}
