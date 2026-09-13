package com.hackerrank.challenge.domain.rules.scoring;

import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Calcula la importancia de una pregunta a partir de cinco factores ponderados
 * por
 * su propio techo de puntos (a mayor peso declarado, mayor techo): tiempo de
 * espera, palabras clave, monto del pedido, estado del pedido y estado de la
 * pregunta (ver DECISIONS.md, sección "Scoring de importancia").
 *
 * <p>
 * El {@link Clock} se recibe en cada cálculo (no se inyecta en el constructor)
 * para que una misma instancia, ya configurada, sirva tanto para el cálculo "al
 * crear" como para el recálculo al vuelo en cada consulta de Operaciones, cada
 * uno
 * con su propio instante de referencia.
 */
public final class QuestionImportanceScorer {

  private final ScoringConfig config;
  private final Map<Pattern, Integer> keywordPatterns;

  public QuestionImportanceScorer(ScoringConfig config) {
    if (config == null) {
      throw new DomainValidationException("La configuración de scoring es obligatoria.");
    }
    this.config = config;
    this.keywordPatterns = config.keywordPoints().entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(
            entry -> compileKeywordPattern(entry.getKey()),
            Map.Entry::getValue));
  }

  /**
   * Calcula el score de una pregunta en un instante dado.
   *
   * <p>
   * Al crear la pregunta, quien llama debe pasar el mismo {@code createdAt} de
   * la pregunta como {@code now}: la brecha de tiempo de espera evaluada así
   * siempre cae en el primer tramo (aporta cero), tal como se decidió para el
   * momento de creación.
   */
  public QuestionScore score(Question question, Order order, Clock clock) {
    if (question == null) {
      throw new DomainValidationException("La pregunta a puntuar es obligatoria.");
    }
    if (order == null) {
      throw new DomainValidationException("El pedido de la pregunta a puntuar es obligatorio.");
    }
    if (clock == null) {
      throw new DomainValidationException("El reloj para calcular el score es obligatorio.");
    }

    Instant now = clock.instant();
    Duration waitingTime = Duration.between(question.getCreatedAt(), now);
    if (waitingTime.isNegative()) {
      waitingTime = Duration.ZERO;
    }

    ScoreBreakdown breakdown = new ScoreBreakdown(
        pointsForWaitingTime(waitingTime),
        pointsForKeywords(question.getQuestionText()),
        pointsForOrderAmount(order.getTotalAmount()),
        config.orderStatusPoints().get(order.getStatus()),
        config.questionStatusPoints().get(question.getStatus()));

    return new QuestionScore(breakdown, classify(breakdown.total()));
  }

  private int pointsForWaitingTime(Duration waitingTime) {
    return pointsForTier(config.waitingTimeTiers(), waitingTime);
  }

  private int pointsForOrderAmount(BigDecimal totalAmount) {
    return pointsForTier(config.orderAmountTiers(), totalAmount);
  }

  private <T extends Comparable<T>> int pointsForTier(List<ScoringConfig.Tier<T>> tiers, T value) {
    for (ScoringConfig.Tier<T> tier : tiers) {
      if (tier.applies(value)) {
        return tier.points();
      }
    }
    // No debería ocurrir: el último tramo configurado no tiene tope.
    throw new IllegalStateException("Ningún tramo configurado cubre el valor " + value + ".");
  }

  /**
   * Suma los puntos de cada palabra distinta encontrada (las repeticiones no
   * vuelven a sumar) y acota el resultado al techo del factor, para que las
   * palabras clave no puedan superar el peso declarado frente al resto.
   */
  private int pointsForKeywords(String questionText) {
    int points = keywordPatterns.entrySet().stream()
        .filter(entry -> entry.getKey().matcher(questionText).find())
        .mapToInt(Map.Entry::getValue)
        .sum();

    return Math.min(points, config.keywordMaxPoints());
  }

  private static Pattern compileKeywordPattern(String keyword) {
    return Pattern.compile(
        "\\b" + Pattern.quote(keyword.toLowerCase(Locale.ROOT)) + "\\b",
        Pattern.CASE_INSENSITIVE);
  }

  private QuestionPriority classify(int total) {
    ScoringConfig.ClassificationThresholds thresholds = config.classificationThresholds();
    if (total >= thresholds.criticalFrom()) {
      return QuestionPriority.CRITICAL;
    }
    if (total >= thresholds.highFrom()) {
      return QuestionPriority.HIGH;
    }
    if (total >= thresholds.mediumFrom()) {
      return QuestionPriority.MEDIUM;
    }
    return QuestionPriority.LOW;
  }
}
