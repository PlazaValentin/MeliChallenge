package com.hackerrank.challenge.domain.notification;

import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;

import java.time.Instant;
import java.util.UUID;

/**
 * Aviso a enviar al vendedor por una pregunta que alcanzo el umbral de
 * notificacion.
 *
 * <p>
 * No lleva el texto de la pregunta ni datos del comprador: los canales loguean
 * lo que envian y los logs no registran informacion PII (ver DECISIONS.md).
 */
public record QuestionNotification(
    UUID questionId,
    UUID orderId,
    UUID sellerId,
    QuestionPriority priority,
    int score,
    Instant occurredAt) {
}
