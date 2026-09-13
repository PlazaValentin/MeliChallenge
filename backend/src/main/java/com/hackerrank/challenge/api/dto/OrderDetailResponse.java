package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.application.output.OrderAggregates;
import com.hackerrank.challenge.application.output.OrderDetail;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de un pedido, con sus lineas y sus preguntas embebidas.
 *
 * <p>
 * Va sin envoltorio porque es un recurso unico, no un listado: el envelope
 * existe para poder agregarle paginacion a una coleccion, y aca no hay
 * coleccion
 * que paginar.
 *
 * <p>
 * Repite los agregados del listado a proposito: el detalle tiene que poder
 * mostrarse sin haber pasado antes por el listado.
 */
public record OrderDetailResponse(
    UUID id,
    OrderStatus status,
    Instant createdAt,
    BuyerResponse buyer,
    List<OrderLineResponse> lines,
    BigDecimal totalAmount,
    boolean hasPendingQuestions,
    QuestionPriority priority,
    List<QuestionResponse> questions) {

  public static OrderDetailResponse from(OrderDetail detail) {
    Order order = detail.order();
    OrderAggregates aggregates = detail.aggregates();

    return new OrderDetailResponse(
        order.getId(),
        order.getStatus(),
        order.getCreatedAt(),
        BuyerResponse.from(order.getBuyer()),
        order.getLines().stream().map(OrderLineResponse::from).toList(),
        aggregates.totalAmount(),
        aggregates.hasPendingQuestions(),
        aggregates.priority().orElse(null),
        detail.questions().stream().map(question -> QuestionResponse.from(question, order)).toList());
  }
}
