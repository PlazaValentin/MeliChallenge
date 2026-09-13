package com.hackerrank.challenge.infrastructure.notification;

import com.hackerrank.challenge.domain.notification.NotificationChannel;
import com.hackerrank.challenge.domain.notification.QuestionNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Envio por email simulado con un log. Unico canal activo por defecto. */
@Component
@ConditionalOnProperty(name = "app.notifications.channels.email.enabled", havingValue = "true")
public class EmailNotificationChannel implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

  @Override
  public String name() {
    return "email";
  }

  @Override
  public void send(QuestionNotification notification) {
    log.info(
        "[EMAIL] Aviso al vendedor {}: la pregunta {} del pedido {} se clasifico {} ({} puntos).",
        notification.sellerId(), notification.questionId(), notification.orderId(),
        notification.priority(), notification.score());
  }
}
