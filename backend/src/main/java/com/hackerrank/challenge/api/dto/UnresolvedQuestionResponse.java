package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.application.output.ScoredQuestion;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.enums.QuestionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la cola de Operaciones.
 *
 * <p>
 * Lleva {@code sellerId} y {@code orderId} para poder ir al detalle del pedido
 * desde la fila, y ademas el estado y el monto de ese pedido, que son dos de los
 * factores del score: tenerlos a la vista evita abrir el detalle solo para
 * entender por que la pregunta puntuo como puntuo.
 *
 * <p>
 * A diferencia de {@link QuestionResponse}, esta si trae el score con su
 * desglose: es la vista cuya razon de ser es priorizar.
 */
public record UnresolvedQuestionResponse(
        UUID id,
        UUID sellerId,
        UUID orderId,
        OrderStatus orderStatus,
        BigDecimal orderTotalAmount,
        UUID productId,
        String questionText,
        String answerText,
        QuestionStatus status,
        Instant createdAt,
        ScoreResponse score) {

    public static UnresolvedQuestionResponse from(ScoredQuestion scored) {
        Question question = scored.question();
        Order order = scored.order();

        return new UnresolvedQuestionResponse(
                question.getId(),
                order.getSellerId(),
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                question.getProductId().orElse(null),
                question.getQuestionText(),
                question.getAnswerText().orElse(null),
                question.getStatus(),
                question.getCreatedAt(),
                ScoreResponse.from(scored.score()));
    }
}
