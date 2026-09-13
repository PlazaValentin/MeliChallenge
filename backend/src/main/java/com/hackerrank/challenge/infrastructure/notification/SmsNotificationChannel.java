package com.hackerrank.challenge.infrastructure.notification;

import com.hackerrank.challenge.domain.notification.NotificationChannel;
import com.hackerrank.challenge.domain.notification.QuestionNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Envio por SMS simulado con un log. Inactivo por configuracion. */
@Component
@ConditionalOnProperty(name = "app.notifications.channels.sms.enabled", havingValue = "true")
public class SmsNotificationChannel implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);

  @Override
  public String name() {
    return "sms";
  }

  @Override
  public void send(QuestionNotification notification) {
    log.info(
        "[SMS] Aviso al vendedor {}: la pregunta {} del pedido {} se clasifico {} ({} puntos).",
        notification.sellerId(), notification.questionId(), notification.orderId(),
        notification.priority(), notification.score());
  }
}
