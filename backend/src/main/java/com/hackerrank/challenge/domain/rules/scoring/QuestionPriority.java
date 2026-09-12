package com.hackerrank.challenge.domain.rules.scoring;

/**
 * Clasificación de importancia de una pregunta, derivada del score total contra
 * {@link ScoringConfig.ClassificationThresholds}. No se persiste (ver
 * DECISIONS.md): se calcula siempre que se necesita.
 */
public enum QuestionPriority {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
