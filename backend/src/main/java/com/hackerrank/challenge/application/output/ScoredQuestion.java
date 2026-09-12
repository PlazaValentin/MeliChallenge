package com.hackerrank.challenge.application.output;

import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.rules.scoring.QuestionScore;

/**
 * Una fila de la cola de Operaciones: la pregunta sin resolver junto con el
 * score calculado al vuelo en el momento de la consulta.
 *
 * <p>
 * Lleva el pedido completo y no solo sus ids porque de el salen tanto el
 * {@code sellerId} y el {@code orderId} que necesita la cola como el monto y el
 * estado que explican el desglose del score.
 *
 * <p>
 * El score viaja aca y no dentro de {@link Question} porque no se persiste: es
 * un calculo puntual, no un atributo de la entidad.
 */
public record ScoredQuestion(Question question, Order order, QuestionScore score) {
}
