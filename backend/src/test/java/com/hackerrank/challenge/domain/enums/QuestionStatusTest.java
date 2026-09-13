package com.hackerrank.challenge.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionStatusTest {

  @Test
  @DisplayName("una pregunta abierta puede pasar a respondida")
  void unaPreguntaAbiertaPuedePasarARespondida() {
    assertThat(QuestionStatus.OPEN.canTransitionTo(QuestionStatus.ANSWERED)).isTrue();
  }

  @Test
  @DisplayName("una pregunta respondida puede pasar a resuelta")
  void unaPreguntaRespondidaPuedePasarAResuelta() {
    assertThat(QuestionStatus.ANSWERED.canTransitionTo(QuestionStatus.RESOLVED)).isTrue();
  }

  /**
   * El ciclo es estrictamente secuencial: responder y resolver son dos acciones
   * distintas del vendedor, asi que no se puede resolver lo que no se respondio.
   */
  @Test
  @DisplayName("una pregunta abierta no puede resolverse sin ser respondida")
  void unaPreguntaAbiertaNoPuedeResolverseSinSerRespondida() {
    assertThat(QuestionStatus.OPEN.canTransitionTo(QuestionStatus.RESOLVED)).isFalse();
  }

  @Test
  @DisplayName("una pregunta resuelta no vuelve atras")
  void unaPreguntaResueltaNoVuelveAtras() {
    assertThat(QuestionStatus.RESOLVED.canTransitionTo(QuestionStatus.OPEN)).isFalse();
    assertThat(QuestionStatus.RESOLVED.canTransitionTo(QuestionStatus.ANSWERED)).isFalse();
    assertThat(QuestionStatus.RESOLVED.isTerminal()).isTrue();
  }

  @Test
  @DisplayName("una pregunta respondida no vuelve a abrirse")
  void unaPreguntaRespondidaNoVuelveAAbrirse() {
    assertThat(QuestionStatus.ANSWERED.canTransitionTo(QuestionStatus.OPEN)).isFalse();
  }

  @ParameterizedTest(name = "{0} no admite transicion hacia si mismo")
  @EnumSource(QuestionStatus.class)
  void ningunEstadoAdmiteTransicionHaciaSiMismo(QuestionStatus status) {
    assertThat(status.canTransitionTo(status)).isFalse();
  }

  @ParameterizedTest(name = "{0} rechaza un destino nulo")
  @EnumSource(QuestionStatus.class)
  void rechazaUnDestinoNulo(QuestionStatus status) {
    assertThat(status.canTransitionTo(null)).isFalse();
  }

  @Test
  @DisplayName("toda pregunta nace abierta")
  void todaPreguntaNaceAbierta() {
    assertThat(QuestionStatus.initial()).isEqualTo(QuestionStatus.OPEN);
  }

  /**
   * Operaciones sigue viendo las respondidas: que el vendedor haya contestado
   * no significa que el comprador haya quedado conforme (ver DECISIONS.md).
   */
  @Test
  @DisplayName("sin resolver abarca tanto las abiertas como las respondidas")
  void sinResolverAbarcaTantoLasAbiertasComoLasRespondidas() {
    assertThat(QuestionStatus.OPEN.isUnresolved()).isTrue();
    assertThat(QuestionStatus.ANSWERED.isUnresolved()).isTrue();
    assertThat(QuestionStatus.RESOLVED.isUnresolved()).isFalse();
  }
}
