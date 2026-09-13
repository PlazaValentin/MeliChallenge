package com.hackerrank.challenge.infrastructure.notification;

import com.hackerrank.challenge.domain.notification.NotificationChannel;
import com.hackerrank.challenge.domain.notification.QuestionNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Envio por Slack simulado con un log. Inactivo por configuracion. */
@Component
@ConditionalOnProperty(name = "app.notifications.channels.slack.enabled", havingValue = "true")
public class SlackNotificationChannel implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(SlackNotificationChannel.class);

  @Override
  public String name() {
    return "slack";
  }

  @Override
  public void send(QuestionNotification notification) {
    log.info(
        "[SLACK] Aviso al vendedor {}: la pregunta {} del pedido {} se clasifico {} ({} puntos).",
        notification.sellerId(), notification.questionId(), notification.orderId(),
        notification.priority(), notification.score());
  }
}
