package com.hackerrank.challenge.application.output;

import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Agregados que acompañan a un pedido tanto en el listado como en el detalle
 * (ver DECISIONS.md, "Listado de pedidos").
 *
 * <p>
 * {@code hasPendingQuestions} y {@code priority} no miran el mismo conjunto de
 * preguntas, y es deliberado: el flag es "respondiste / no respondiste" y por
 * lo
 * tanto solo mira las {@code OPEN}, mientras que la prioridad se calcula sobre
 * las sin resolver ({@code OPEN} y {@code ANSWERED}), que son las que siguen en
 * el radar de Operaciones. Un pedido con todas sus preguntas respondidas pero
 * ninguna resuelta tiene el flag apagado y, aun asi, prioridad presente.
 *
 * @param priority vacio cuando el pedido no tiene preguntas sin resolver. No se
 *                 colapsa a {@code LOW} porque "sin preguntas" y "preguntas
 *                 triviales" no son lo mismo.
 */
public record OrderAggregates(
    BigDecimal totalAmount,
    boolean hasPendingQuestions,
    Optional<QuestionPriority> priority) {
}
