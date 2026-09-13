package com.hackerrank.challenge.support;

import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.enums.QuestionStatus;
import com.hackerrank.challenge.domain.rules.scoring.ScoringConfig;
import com.hackerrank.challenge.domain.rules.scoring.ScoringConfig.ClassificationThresholds;
import com.hackerrank.challenge.domain.rules.scoring.ScoringConfig.Tier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuraciones de scoring para los tests.
 *
 * <p>
 * {@link #standard()} replica los valores de {@code application.properties} en
 * lugar de leerlos, para no depender del contexto de Spring. Si esos valores
 * cambian, los tests que afirman puntajes concretos deben cambiar con ellos:
 * son la traduccion ejecutable de lo acordado en DECISIONS.md.
 */
public final class ScoringConfigs {

  private ScoringConfigs() {
  }

  public static ScoringConfig standard() {
    return new ScoringConfig(
        List.of(
            Tier.upTo(Duration.ofHours(24), 0),
            Tier.upTo(Duration.ofHours(72), 20),
            Tier.upTo(Duration.ofHours(168), 40),
            Tier.unbounded(60)),
        Map.of(
            "urgente", 10,
            "roto", 10,
            "incompleto", 10,
            "enojado", 10,
            "estafa", 10,
            "defectuoso", 10,
            "pesimo", 10,
            "indignado", 10,
            "reclamo", 10,
            "devolucion", 10),
        50,
        List.of(
            Tier.upTo(new BigDecimal("49999.99"), 0),
            Tier.upTo(new BigDecimal("149999.99"), 15),
            Tier.upTo(new BigDecimal("499999.99"), 25),
            Tier.unbounded(40)),
        Map.of(
            OrderStatus.PENDING, 10,
            OrderStatus.CONFIRMED, 10,
            OrderStatus.SHIPPED, 5,
            OrderStatus.DELIVERED, 0,
            OrderStatus.CANCELLED, 30),
        Map.of(
            QuestionStatus.OPEN, 15,
            QuestionStatus.ANSWERED, 5,
            QuestionStatus.RESOLVED, 0),
        new ClassificationThresholds(50, 90, 130));
  }

  /**
   * Config donde el unico factor que suma es una palabra clave que vale lo que
   * se le indique. Permite fijar un score total exacto para ejercitar los
   * bordes de la clasificacion, que con los puntajes reales (todos multiplos de
   * cinco) no serian alcanzables.
   */
  public static ScoringConfig onlyKeyword(int keywordPoints) {
    // El techo debe ser positivo, asi que para el caso de cero puntos se usa 1:
    // igual acota a lo que suma la unica palabra del diccionario.
    return new ScoringConfig(
        List.of(Tier.unbounded(0)),
        Map.of(SINGLE_KEYWORD, keywordPoints),
        Math.max(keywordPoints, 1),
        List.of(Tier.unbounded(0)),
        zeroFor(OrderStatus.values()),
        zeroFor(QuestionStatus.values()),
        new ClassificationThresholds(50, 90, 130));
  }

  public static final String SINGLE_KEYWORD = "disparador";

  private static <E extends Enum<E>> Map<E, Integer> zeroFor(E[] values) {
    return java.util.Arrays.stream(values)
        .collect(java.util.stream.Collectors.toMap(value -> value, value -> 0));
  }
}
