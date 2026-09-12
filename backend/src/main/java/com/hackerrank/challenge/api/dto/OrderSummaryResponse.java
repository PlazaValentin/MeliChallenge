package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.application.output.OrderAggregates;
import com.hackerrank.challenge.application.output.SellerOrderSummary;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila del listado de pedidos del vendedor.
 *
 * <p>
 * No trae lineas ni preguntas: para eso esta el detalle. Tampoco trae el score
 * numerico, solo la clasificacion derivada, que es lo que el vendedor necesita
 * para leer de un vistazo (ver DECISIONS.md).
 *
 * @param priority nulo cuando el pedido no tiene preguntas sin resolver. No se
 *                 colapsa a {@code LOW}: "sin preguntas" y "preguntas
 *                 triviales"
 *                 no son lo mismo.
 */
public record OrderSummaryResponse(
    UUID id,
    OrderStatus status,
    Instant createdAt,
    BuyerResponse buyer,
    BigDecimal totalAmount,
    boolean hasPendingQuestions,
    QuestionPriority priority) {

  public static OrderSummaryResponse from(SellerOrderSummary summary) {
    Order order = summary.order();
    OrderAggregates aggregates = summary.aggregates();

    return new OrderSummaryResponse(
        order.getId(),
        order.getStatus(),
        order.getCreatedAt(),
        BuyerResponse.from(order.getBuyer()),
        aggregates.totalAmount(),
        aggregates.hasPendingQuestions(),
        aggregates.priority().orElse(null));
  }
}
