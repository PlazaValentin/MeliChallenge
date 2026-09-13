package com.hackerrank.challenge.application.event;

import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;

import java.time.Instant;
import java.util.UUID;

/**
 * Se publica despues de guardar una pregunta. La clasificacion viaja ya
 * calculada
 * porque el score no se persiste: recalcularlo en el listener daria otro valor,
 * al haber pasado el tiempo entre la publicacion y el consumo.
 *
 * <p>
 * En este momento el factor tiempo aporta cero (la pregunta recien nace), asi
 * que el score es el de creacion y no el que vera Operaciones mas adelante.
 */
public record QuestionCreatedEvent(
    UUID questionId,
    UUID orderId,
    UUID sellerId,
    QuestionPriority priority,
    int score,
    Instant occurredAt) {
}
