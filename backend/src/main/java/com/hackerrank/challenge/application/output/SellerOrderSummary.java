package com.hackerrank.challenge.application.output;

import com.hackerrank.challenge.domain.entity.Order;

/**
 * Una fila del listado de pedidos del vendedor: el pedido junto con sus
 * agregados derivados. No trae las preguntas: el listado solo necesita saber si
 * hay pendientes y con que prioridad, no cuales son.
 */
public record SellerOrderSummary(Order order, OrderAggregates aggregates) {
}
