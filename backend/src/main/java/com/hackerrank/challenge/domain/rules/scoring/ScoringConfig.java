package com.hackerrank.challenge.domain.rules.scoring;

import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.enums.QuestionStatus;
import com.hackerrank.challenge.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuración tipada del scoring de importancia de preguntas. Puro dominio:
 * no
 * conoce Spring ni de dónde vienen sus valores. Quien arma esta instancia
 * (infraestructura) es responsable de leerla desde donde corresponda (hoy,
 * {@code application.properties}).
 *
 * <p>
 * Cada factor se representa como una lista ordenada de tramos con un límite y
 * un puntaje; el último tramo de cada lista corresponde al "y más" (sin límite
 * superior). Ver DECISIONS.md, sección "Scoring de importancia", para el porqué
 * de
 * la normalización por brechas en lugar de valores absolutos.
 */
public record ScoringConfig(
    List<Tier<Duration>> waitingTimeTiers,
    Map<String, Integer> keywordPoints,
    List<Tier<BigDecimal>> orderAmountTiers,
    Map<OrderStatus, Integer> orderStatusPoints,
    Map<QuestionStatus, Integer> questionStatusPoints,
    ClassificationThresholds classificationThresholds) {

  public ScoringConfig {
    requireNonEmpty(waitingTimeTiers, "Los tramos de tiempo de espera");
    requireNonEmpty(keywordPoints, "El diccionario de palabras clave");
    requireNonEmpty(orderAmountTiers, "Los tramos de monto del pedido");
    requireComplete(orderStatusPoints, OrderStatus.values(), "el estado del pedido");
    requireComplete(questionStatusPoints, QuestionStatus.values(), "el estado de la pregunta");
    if (classificationThresholds == null) {
      throw new DomainValidationException("Los umbrales de clasificación son obligatorios.");
    }
  }

  private static void requireNonEmpty(List<?> list, String fieldDescription) {
    if (list == null || list.isEmpty()) {
      throw new DomainValidationException(fieldDescription + " no pueden estar vacíos.");
    }
  }

  private static void requireNonEmpty(Map<?, ?> map, String fieldDescription) {
    if (map == null || map.isEmpty()) {
      throw new DomainValidationException(fieldDescription + " no puede estar vacío.");
    }
  }

  private static <E extends Enum<E>> void requireComplete(
      Map<E, Integer> map, E[] allValues, String fieldDescription) {
    if (map == null || map.size() != allValues.length) {
      throw new DomainValidationException(
          "Debe configurarse un puntaje para cada valor de " + fieldDescription + ".");
    }
  }

  /**
   * Tramo de una brecha: puntúa {@code points} a todo valor menor o igual a
   * {@code upperBound}. Se recorren en orden y gana el primero que aplica.
   * {@code upperBound} vacío representa el último tramo, sin tope ("y más").
   */
  public record Tier<T extends Comparable<T>>(Optional<T> upperBound, int points) {

    public Tier {
      if (upperBound == null) {
        throw new DomainValidationException("El límite del tramo es obligatorio (o vacío para 'sin tope').");
      }
    }

    public static <T extends Comparable<T>> Tier<T> upTo(T upperBound, int points) {
      return new Tier<>(Optional.of(upperBound), points);
    }

    public static <T extends Comparable<T>> Tier<T> unbounded(int points) {
      return new Tier<T>(Optional.<T>empty(), points);
    }

    public boolean applies(T value) {
      return upperBound.map(bound -> value.compareTo(bound) <= 0).orElse(true);
    }
  }

  /**
   * Umbrales, sobre el score total, que determinan la clasificación de la
   * pregunta.
   */
  public record ClassificationThresholds(int mediumFrom, int highFrom, int criticalFrom) {

    public ClassificationThresholds {
      if (!(mediumFrom < highFrom && highFrom < criticalFrom)) {
        throw new DomainValidationException(
            "Los umbrales de clasificación deben ser estrictamente crecientes.");
      }
    }
  }
}
