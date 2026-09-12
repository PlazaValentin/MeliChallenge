package com.hackerrank.challenge.domain.repository;

import com.hackerrank.challenge.domain.entity.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de persistencia para {@link Order}. El dominio define la operación
 * que
 * necesita; la infraestructura decide cómo cumplirla (hoy, en memoria).
 */
public interface OrderRepository {

  Order save(Order order);

  Optional<Order> findById(UUID id);

  /**
   * Pedidos de un vendedor que cumplen los filtros combinables con AND, ordenados
   * por fecha de creación descendente. Los filtros van acá y no en el service:
   * es lo que haría una base de datos real, y contiene el cambio si el día de
   * mañana se migra a una consulta SQL.
   */
  List<Order> findBySeller(UUID sellerId, OrderSearchCriteria criteria);
}
