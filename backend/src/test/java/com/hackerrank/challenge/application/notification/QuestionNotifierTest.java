package com.hackerrank.challenge.application.notification;

import com.hackerrank.challenge.domain.notification.NotificationChannel;
import com.hackerrank.challenge.domain.notification.NotificationPolicy;
import com.hackerrank.challenge.domain.notification.QuestionNotification;
import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;
import com.hackerrank.challenge.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Se instancia directo, sin asincronia ni contexto de Spring: la decision de
 * notificar y el despacho viven aca justamente para poder ejercitarse asi.
 */
@ExtendWith(MockitoExtension.class)
class QuestionNotifierTest {

  @Mock
  private NotificationChannel email;

  @Mock
  private NotificationChannel slack;

  private final NotificationPolicy policy = new NotificationPolicy(QuestionPriority.HIGH);

  @ParameterizedTest(name = "una pregunta {0} no genera aviso")
  @EnumSource(value = QuestionPriority.class, names = { "LOW", "MEDIUM" })
  void noAvisaPorPreguntasQueNoAlcanzanElUmbral(QuestionPriority priority) {
    QuestionNotifier notifier = new QuestionNotifier(List.of(email), policy);

    boolean notified = notifier.notifyIfNeeded(notificationWith(priority));

    assertThat(notified).isFalse();
    verify(email, never()).send(any());
  }

  @ParameterizedTest(name = "una pregunta {0} genera aviso")
  @EnumSource(value = QuestionPriority.class, names = { "HIGH", "CRITICAL" })
  void avisaPorPreguntasQueAlcanzanElUmbral(QuestionPriority priority) {
    QuestionNotifier notifier = new QuestionNotifier(List.of(email), policy);
    QuestionNotification notification = notificationWith(priority);

    boolean notified = notifier.notifyIfNeeded(notification);

    assertThat(notified).isTrue();
    verify(email).send(notification);
  }

  @Test
  @DisplayName("el aviso sale por todos los canales activos")
  void elAvisoSalePorTodosLosCanalesActivos() {
    QuestionNotifier notifier = new QuestionNotifier(List.of(email, slack), policy);
    QuestionNotification notification = notificationWith(QuestionPriority.CRITICAL);

    notifier.notifyIfNeeded(notification);

    verify(email).send(notification);
    verify(slack).send(notification);
  }

  /**
   * Un canal caido no puede arrastrar a los demas: el fallo se loguea y el
   * recorrido continua (ver DECISIONS.md).
   */
  @Test
  @DisplayName("si un canal falla, los demas igual reciben el aviso")
  void siUnCanalFallaLosDemasIgualRecibenElAviso() {
    willThrow(new RuntimeException("canal caido")).given(email).send(any());
    QuestionNotifier notifier = new QuestionNotifier(List.of(email, slack), policy);
    QuestionNotification notification = notificationWith(QuestionPriority.HIGH);

    boolean notified = notifier.notifyIfNeeded(notification);

    assertThat(notified).isTrue();
    verify(slack).send(notification);
  }

  @Test
  @DisplayName("si todos los canales fallan el aviso no se propaga como error")
  void siTodosLosCanalesFallanElAvisoNoSePropagaComoError() {
    willThrow(new RuntimeException("canal caido")).given(email).send(any());
    willThrow(new RuntimeException("canal caido")).given(slack).send(any());
    QuestionNotifier notifier = new QuestionNotifier(List.of(email, slack), policy);

    boolean notified = notifier.notifyIfNeeded(notificationWith(QuestionPriority.CRITICAL));

    assertThat(notified).isTrue();
  }

  @Test
  @DisplayName("sin canales activos no hay aviso posible")
  void sinCanalesActivosNoHayAvisoPosible() {
    QuestionNotifier notifier = new QuestionNotifier(List.of(), policy);

    boolean notified = notifier.notifyIfNeeded(notificationWith(QuestionPriority.CRITICAL));

    assertThat(notified).isFalse();
  }

  private static QuestionNotification notificationWith(QuestionPriority priority) {
    return new QuestionNotification(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), priority, 100, TestData.NOW);
  }
}
