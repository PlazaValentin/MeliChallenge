package com.hackerrank.challenge.application.notification;

import com.hackerrank.challenge.application.event.QuestionCreatedEvent;
import com.hackerrank.challenge.domain.notification.QuestionNotification;
import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;
import com.hackerrank.challenge.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Se ejercita el listener instanciado directo, sin asincronia real: esperar a
 * otro hilo haria el test flaky sin verificar nada que no se vea aca.
 */
@ExtendWith(MockitoExtension.class)
class QuestionCreatedListenerTest {

  @Mock
  private QuestionNotifier notifier;

  @InjectMocks
  private QuestionCreatedListener listener;

  @Captor
  private ArgumentCaptor<QuestionNotification> notificationCaptor;

  /**
   * La clasificacion viaja calculada en el evento: el listener no la recalcula,
   * porque el score no se persiste y entre publicar y consumir pasa el tiempo.
   */
  @Test
  @DisplayName("el aviso conserva los datos que traia el evento")
  void elAvisoConservaLosDatosQueTraiaElEvento() {
    QuestionCreatedEvent event = new QuestionCreatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        QuestionPriority.CRITICAL,
        135,
        TestData.NOW);

    listener.onQuestionCreated(event);

    verify(notifier).notifyIfNeeded(notificationCaptor.capture());
    QuestionNotification notification = notificationCaptor.getValue();
    assertThat(notification.questionId()).isEqualTo(event.questionId());
    assertThat(notification.orderId()).isEqualTo(event.orderId());
    assertThat(notification.sellerId()).isEqualTo(event.sellerId());
    assertThat(notification.priority()).isEqualTo(event.priority());
    assertThat(notification.score()).isEqualTo(event.score());
    assertThat(notification.occurredAt()).isEqualTo(event.occurredAt());
  }

  /**
   * Decidir si corresponde avisar es del notifier: el listener delega siempre,
   * incluso cuando la clasificacion no alcanza el umbral.
   */
  @Test
  @DisplayName("el listener delega la decision sin filtrar por clasificacion")
  void elListenerDelegaLaDecisionSinFiltrarPorClasificacion() {
    QuestionCreatedEvent event = new QuestionCreatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        QuestionPriority.LOW,
        25,
        TestData.NOW);

    listener.onQuestionCreated(event);

    verify(notifier).notifyIfNeeded(notificationCaptor.capture());
    assertThat(notificationCaptor.getValue().priority()).isEqualTo(QuestionPriority.LOW);
  }
}
