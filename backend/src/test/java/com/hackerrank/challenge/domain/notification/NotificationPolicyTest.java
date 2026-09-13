package com.hackerrank.challenge.domain.notification;

import com.hackerrank.challenge.domain.exception.DomainValidationException;
import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationPolicyTest {

  private final NotificationPolicy policy = new NotificationPolicy(QuestionPriority.HIGH);

  @Test
  @DisplayName("se notifica desde la prioridad minima hacia arriba")
  void seNotificaDesdeLaPrioridadMinimaHaciaArriba() {
    assertThat(policy.shouldNotify(QuestionPriority.HIGH)).isTrue();
    assertThat(policy.shouldNotify(QuestionPriority.CRITICAL)).isTrue();
  }

  @Test
  @DisplayName("no se notifica por debajo de la prioridad minima")
  void noSeNotificaPorDebajoDeLaPrioridadMinima() {
    assertThat(policy.shouldNotify(QuestionPriority.LOW)).isFalse();
    assertThat(policy.shouldNotify(QuestionPriority.MEDIUM)).isFalse();
  }

  /**
   * El umbral vive en configuracion: subirlo a CRITICAL deja fuera lo que antes
   * se notificaba, sin tocar codigo.
   */
  @Test
  @DisplayName("el umbral se corre con la configuracion")
  void elUmbralSeCorreConLaConfiguracion() {
    NotificationPolicy onlyCritical = new NotificationPolicy(QuestionPriority.CRITICAL);

    assertThat(onlyCritical.shouldNotify(QuestionPriority.HIGH)).isFalse();
    assertThat(onlyCritical.shouldNotify(QuestionPriority.CRITICAL)).isTrue();
  }

  @Test
  @DisplayName("una clasificacion ausente no dispara aviso")
  void unaClasificacionAusenteNoDisparaAviso() {
    assertThat(policy.shouldNotify(null)).isFalse();
  }

  @Test
  void rechazaConstruirseSinPrioridadMinima() {
    assertThatThrownBy(() -> new NotificationPolicy(null))
        .isInstanceOf(DomainValidationException.class);
  }
}
