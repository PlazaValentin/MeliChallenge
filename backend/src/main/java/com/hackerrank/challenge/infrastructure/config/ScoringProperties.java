package com.hackerrank.challenge.infrastructure.config;

import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.enums.QuestionStatus;
import com.hackerrank.challenge.domain.rules.scoring.ScoringConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Lee {@code app.scoring.*} de {@code application.properties} y arma el
 * {@link ScoringConfig} tipado que consume el dominio. Es el único punto que
 * conoce Spring en todo el camino de configuración del scoring: el dominio
 * (`domain.rules.scoring`) no depende de este paquete.
 *
 * <p>
 * Cada lista de tramos ({@code tiers}) se declara en
 * {@code application.properties}
 * como una serie de pares límite/puntos; el último elemento de la lista es el
 * tramo
 * "sin tope" y no lleva límite explícito, según convención documentada en el
 * properties.
 */
@ConfigurationProperties(prefix = "app.scoring")
public class ScoringProperties {

  private List<TierProperties<Long>> waitingTimeHoursTiers;
  private Map<String, Integer> keywordPoints;
  private List<TierProperties<BigDecimal>> orderAmountTiers;
  private Map<OrderStatus, Integer> orderStatusPoints;
  private Map<QuestionStatus, Integer> questionStatusPoints;
  private ClassificationThresholdsProperties classificationThresholds;

  public List<TierProperties<Long>> getWaitingTimeHoursTiers() {
    return waitingTimeHoursTiers;
  }

  public void setWaitingTimeHoursTiers(List<TierProperties<Long>> waitingTimeHoursTiers) {
    this.waitingTimeHoursTiers = waitingTimeHoursTiers;
  }

  public Map<String, Integer> getKeywordPoints() {
    return keywordPoints;
  }

  public void setKeywordPoints(Map<String, Integer> keywordPoints) {
    this.keywordPoints = keywordPoints;
  }

  public List<TierProperties<BigDecimal>> getOrderAmountTiers() {
    return orderAmountTiers;
  }

  public void setOrderAmountTiers(List<TierProperties<BigDecimal>> orderAmountTiers) {
    this.orderAmountTiers = orderAmountTiers;
  }

  public Map<OrderStatus, Integer> getOrderStatusPoints() {
    return orderStatusPoints;
  }

  public void setOrderStatusPoints(Map<OrderStatus, Integer> orderStatusPoints) {
    this.orderStatusPoints = orderStatusPoints;
  }

  public Map<QuestionStatus, Integer> getQuestionStatusPoints() {
    return questionStatusPoints;
  }

  public void setQuestionStatusPoints(Map<QuestionStatus, Integer> questionStatusPoints) {
    this.questionStatusPoints = questionStatusPoints;
  }

  public ClassificationThresholdsProperties getClassificationThresholds() {
    return classificationThresholds;
  }

  public void setClassificationThresholds(ClassificationThresholdsProperties classificationThresholds) {
    this.classificationThresholds = classificationThresholds;
  }

  public ScoringConfig toScoringConfig() {
    return new ScoringConfig(
        toDurationTiers(waitingTimeHoursTiers),
        keywordPoints,
        toTiers(orderAmountTiers),
        orderStatusPoints,
        questionStatusPoints,
        new ScoringConfig.ClassificationThresholds(
            classificationThresholds.getMediumFrom(),
            classificationThresholds.getHighFrom(),
            classificationThresholds.getCriticalFrom()));
  }

  private static List<ScoringConfig.Tier<Duration>> toDurationTiers(List<TierProperties<Long>> source) {
    return source.stream()
        .map(tier -> tier.upperBound() == null
            ? ScoringConfig.Tier.<Duration>unbounded(tier.points())
            : ScoringConfig.Tier.upTo(Duration.ofHours(tier.upperBound()), tier.points()))
        .toList();
  }

  private static <T extends Comparable<T>> List<ScoringConfig.Tier<T>> toTiers(
      List<TierProperties<T>> source) {
    return source.stream()
        .map(tier -> tier.upperBound() == null
            ? ScoringConfig.Tier.<T>unbounded(tier.points())
            : ScoringConfig.Tier.upTo(tier.upperBound(), tier.points()))
        .toList();
  }

  /**
   * Un tramo tal como se declara en el properties: límite superior opcional y
   * puntos.
   */
  public record TierProperties<T>(T upperBound, int points) {
  }

  public static class ClassificationThresholdsProperties {
    private int mediumFrom;
    private int highFrom;
    private int criticalFrom;

    public int getMediumFrom() {
      return mediumFrom;
    }

    public void setMediumFrom(int mediumFrom) {
      this.mediumFrom = mediumFrom;
    }

    public int getHighFrom() {
      return highFrom;
    }

    public void setHighFrom(int highFrom) {
      this.highFrom = highFrom;
    }

    public int getCriticalFrom() {
      return criticalFrom;
    }

    public void setCriticalFrom(int criticalFrom) {
      this.criticalFrom = criticalFrom;
    }
  }
}
