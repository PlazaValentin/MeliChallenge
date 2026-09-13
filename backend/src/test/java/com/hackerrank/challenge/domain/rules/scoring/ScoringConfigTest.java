package com.hackerrank.challenge.domain.rules.scoring;

import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.enums.QuestionStatus;
import com.hackerrank.challenge.domain.exception.DomainValidationException;
import com.hackerrank.challenge.domain.rules.scoring.ScoringConfig.ClassificationThresholds;
import com.hackerrank.challenge.domain.rules.scoring.ScoringConfig.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La configuracion se valida al construirse: una config incompleta o
 * incoherente
 * debe fallar al levantar la app, no al puntuar la primera pregunta.
 */
class ScoringConfigTest {

  @Test
  @DisplayName("los umbrales de clasificacion deben ser estrictamente crecientes")
  void losUmbralesDeClasificacionDebenSerEstrictamenteCrecientes() {
    assertThatThrownBy(() -> new ClassificationThresholds(90, 50, 130))
        .isInstanceOf(DomainValidationException.class);

    assertThatThrownBy(() -> new ClassificationThresholds(50, 50, 130))
        .isInstanceOf(DomainValidationException.class);

    assertThatThrownBy(() -> new ClassificationThresholds(50, 130, 130))
        .isInstanceOf(DomainValidationException.class);
  }

  /**
   * Si faltara el puntaje de un estado, el scorer sumaria null al puntuar una
   * pregunta de ese estado: mejor que falle al configurarse.
   */
  @Test
  @DisplayName("debe configurarse un puntaje para cada estado de pedido")
  void debeConfigurarseUnPuntajeParaCadaEstadoDePedido() {
    assertThatThrownBy(() -> configWith(
        Map.of(OrderStatus.PENDING, 10),
        completeQuestionStatusPoints()))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  @DisplayName("debe configurarse un puntaje para cada estado de pregunta")
  void debeConfigurarseUnPuntajeParaCadaEstadoDePregunta() {
    assertThatThrownBy(() -> configWith(
        completeOrderStatusPoints(),
        Map.of(QuestionStatus.OPEN, 15)))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  @DisplayName("el ultimo tramo sin tope aplica a cualquier valor")
  void elUltimoTramoSinTopeAplicaACualquierValor() {
    Tier<Duration> unbounded = Tier.unbounded(60);

    assertThat(unbounded.applies(Duration.ofDays(365))).isTrue();
    assertThat(unbounded.applies(Duration.ZERO)).isTrue();
  }

  @Test
  @DisplayName("un tramo con tope incluye su propio limite")
  void unTramoConTopeIncluyeSuPropioLimite() {
    Tier<Duration> upTo24Hours = Tier.upTo(Duration.ofHours(24), 0);

    assertThat(upTo24Hours.applies(Duration.ofHours(24))).isTrue();
    assertThat(upTo24Hours.applies(Duration.ofHours(25))).isFalse();
  }

  private static ScoringConfig configWith(
      Map<OrderStatus, Integer> orderStatusPoints,
      Map<QuestionStatus, Integer> questionStatusPoints) {

    return new ScoringConfig(
        List.of(Tier.unbounded(0)),
        Map.of("urgente", 10),
        List.of(Tier.<BigDecimal>unbounded(0)),
        orderStatusPoints,
        questionStatusPoints,
        new ClassificationThresholds(50, 90, 130));
  }

  private static Map<OrderStatus, Integer> completeOrderStatusPoints() {
    return Map.of(
        OrderStatus.PENDING, 10,
        OrderStatus.CONFIRMED, 10,
        OrderStatus.SHIPPED, 5,
        OrderStatus.DELIVERED, 0,
        OrderStatus.CANCELLED, 30);
  }

  private static Map<QuestionStatus, Integer> completeQuestionStatusPoints() {
    return Map.of(
        QuestionStatus.OPEN, 15,
        QuestionStatus.ANSWERED, 5,
        QuestionStatus.RESOLVED, 0);
  }
}
