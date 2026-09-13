package com.hackerrank.challenge.application.notification;

import com.hackerrank.challenge.domain.notification.NotificationChannel;
import com.hackerrank.challenge.domain.notification.NotificationPolicy;
import com.hackerrank.challenge.domain.notification.QuestionNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decide si una pregunta amerita aviso y, en ese caso, la envia por todos los
 * canales activos.
 *
 * <p>
 * Recibe la lista de canales ya resuelta por Spring: solo llegan los que estan
 * habilitados por configuracion, asi que sumar un canal no toca esta clase.
 */
@Component
public class QuestionNotifier {

  private static final Logger log = LoggerFactory.getLogger(QuestionNotifier.class);

  private final List<NotificationChannel> channels;
  private final NotificationPolicy policy;

  public QuestionNotifier(List<NotificationChannel> channels, NotificationPolicy policy) {
    this.channels = channels;
    this.policy = policy;
  }

  /** @return true si la notificacion se intento por al menos un canal. */
  public boolean notifyIfNeeded(QuestionNotification notification) {
    if (!policy.shouldNotify(notification.priority())) {
      log.debug(
          "Pregunta {} clasificada {} ({} puntos): no alcanza el umbral de notificacion.",
          notification.questionId(), notification.priority(), notification.score());
      return false;
    }

    if (channels.isEmpty()) {
      log.warn(
          "Pregunta {} clasificada {} amerita aviso pero no hay canales activos.",
          notification.questionId(), notification.priority());
      return false;
    }

    channels.forEach(channel -> sendSafely(channel, notification));
    return true;
  }

  /**
   * Un canal caido no puede impedir el envio por los demas, asi que el fallo se
   * registra y el recorrido continua. No hay reintentos (ver DECISIONS.md).
   */
  private void sendSafely(NotificationChannel channel, QuestionNotification notification) {
    try {
      channel.send(notification);
    } catch (RuntimeException error) {
      log.error(
          "Fallo el envio de la pregunta {} por el canal {}: {}",
          notification.questionId(), channel.name(), error.getMessage());
    }
  }
}
