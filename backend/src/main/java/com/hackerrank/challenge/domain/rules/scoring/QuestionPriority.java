package com.hackerrank.challenge.domain.rules.scoring;

/**
 * Clasificación de importancia de una pregunta, derivada del score total contra
 * {@link ScoringConfig.ClassificationThresholds}. No se persiste (ver
 * DECISIONS.md): se calcula siempre que se necesita.
 *
 * <p>
 * <strong>El orden de declaración es significativo:</strong> las constantes van
 * de menos a más grave, y la prioridad de un pedido se deriva tomando el máximo
 * de las de sus preguntas sin resolver según el orden natural del enum.
 * Reordenarlas cambiaría ese cálculo en silencio, sin romper la compilación ni
 * fallar en ningún lado: la prioridad del pedido simplemente pasaría a ser
 * otra.
 * Agregar niveles nuevos es seguro mientras se inserten en la posición que les
 * corresponde por severidad.
 */
public enum QuestionPriority {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
