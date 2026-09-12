package com.hackerrank.challenge.domain.repository;

import com.hackerrank.challenge.domain.entity.Order;

import java.util.Collection;
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
   * Resuelve varios pedidos en una sola llamada, para no incurrir en un N+1
   * cuando
   * hay que traer los pedidos de un conjunto de preguntas.
   *
   * <p>
   * Los ids que no existan simplemente no aparecen en el resultado: decidir si
   * esa
   * ausencia es tolerable o un error es responsabilidad de quien llama, no del
   * repositorio. El orden del resultado no esta garantizado.
   */
  List<Order> findAllById(Collection<UUID> ids);

  /**
   * Pedidos de un vendedor que cumplen los filtros combinables con AND, ordenados
   * por fecha de creación descendente. Los filtros van acá y no en el service:
   * es lo que haría una base de datos real, y contiene el cambio si el día de
   * mañana se migra a una consulta SQL.
   */
  List<Order> findBySeller(UUID sellerId, OrderSearchCriteria criteria);
}
