package com.hackerrank.challenge.application.output;

import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;

import java.util.List;

/**
 * Detalle de un pedido con sus preguntas embebidas y los mismos agregados que
 * expone el listado.
 *
 * <p>
 * Las preguntas vienen juntas con el pedido porque se usan juntas; separarlas
 * obligaria al frontend a hacer dos llamadas por cada pedido consultado (ver
 * DECISIONS.md).
 */
public record OrderDetail(Order order, List<Question> questions, OrderAggregates aggregates) {
}
