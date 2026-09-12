package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.enums.QuestionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Pregunta tal como se ve dentro del detalle de un pedido.
 *
 * <p>
 * No lleva score ni prioridad individual: el vendedor ve la clasificacion
 * agregada del pedido, no el detalle de cada pregunta. El score por pregunta es
 * cosa de la cola de Operaciones (ver {@link UnresolvedQuestionResponse}).
 *
 * @param productId  nulo cuando la pregunta es sobre el pedido en general y no
 *                   sobre un item puntual.
 * @param answerText nulo mientras la pregunta no haya sido respondida.
 */
public record QuestionResponse(
    UUID id,
    UUID productId,
    String questionText,
    String answerText,
    QuestionStatus status,
    Instant createdAt) {

  public static QuestionResponse from(Question question) {
    return new QuestionResponse(
        question.getId(),
        question.getProductId().orElse(null),
        question.getQuestionText(),
        question.getAnswerText().orElse(null),
        question.getStatus(),
        question.getCreatedAt());
  }
}
