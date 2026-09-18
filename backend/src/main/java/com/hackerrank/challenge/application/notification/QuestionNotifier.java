package com.hackerrank.challenge.application.notification;

import com.hackerrank.challenge.domain.notification.NotificationChannel;
import com.hackerrank.challenge.domain.notification.NotificationPolicy;
import com.hackerrank.challenge.domain.notification.QuestionNotification;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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

  /**
   * Despachos por canal y resultado. Las dos etiquetas son conjuntos chicos y
   * cerrados: no crecen con el trafico (ver PLAN_ANEXO_OTEL.md, Decision 17).
   */
  private static final LongCounter DISPATCHES = GlobalOpenTelemetry
      .getMeter("com.hackerrank.challenge")
      .counterBuilder("notifications.dispatched")
      .setDescription("Notificaciones despachadas, por canal y resultado.")
      .setUnit("{notification}")
      .build();

  private static final AttributeKey<String> CHANNEL = AttributeKey.stringKey("notification.channel");
  private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("notification.outcome");

  private final List<NotificationChannel> channels;
  private final NotificationPolicy policy;

  public QuestionNotifier(List<NotificationChannel> channels, NotificationPolicy policy) {
    this.channels = channels;
    this.policy = policy;
  }

  /**
   * Corre en el hilo del listener {@code @Async}, o sea despues de que el
   * request HTTP respondio. El contexto de traza lo propaga el agente, asi que
   * este span se cuelga del span de entrada que creo la pregunta: es lo que
   * hace visible la continuidad a traves de la frontera asincronica.
   *
   * @return true si la notificacion se intento por al menos un canal.
   */
  @WithSpan("notifications.dispatch")
  public boolean notifyIfNeeded(QuestionNotification notification) {
    Span.current()
        .setAttribute("question.id", notification.questionId().toString())
        .setAttribute("question.priority", notification.priority().name())
        .setAttribute("question.score", notification.score())
        .setAttribute("notification.channels.active", channels.size());

    if (!policy.shouldNotify(notification.priority())) {
      Span.current().setAttribute("notification.skipped.reason", "below-threshold");
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
      DISPATCHES.add(1, Attributes.of(CHANNEL, channel.name(), OUTCOME, "sent"));
    } catch (RuntimeException error) {
      DISPATCHES.add(1, Attributes.of(CHANNEL, channel.name(), OUTCOME, "failed"));
      Span.current().recordException(error);
      log.error(
          "Fallo el envio de la pregunta {} por el canal {}: {}",
          notification.questionId(), channel.name(), error.getMessage());
    }
  }
}
