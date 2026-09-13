package com.hackerrank.challenge.application.notification;

import com.hackerrank.challenge.application.event.QuestionCreatedEvent;
import com.hackerrank.challenge.domain.notification.QuestionNotification;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Escucha la creacion de preguntas y delega la decision de notificar.
 *
 * <p>
 * Es {@code @Async} para que el request HTTP responda sin esperar el envio. La
 * logica de decidir y despachar vive en {@link QuestionNotifier}, que se puede
 * testear sin asincronia (ver DECISIONS.md, "Testing").
 */
@Component
public class QuestionCreatedListener {

  private final QuestionNotifier notifier;

  public QuestionCreatedListener(QuestionNotifier notifier) {
    this.notifier = notifier;
  }

  @Async
  @EventListener
  public void onQuestionCreated(QuestionCreatedEvent event) {
    notifier.notifyIfNeeded(new QuestionNotification(
        event.questionId(),
        event.orderId(),
        event.sellerId(),
        event.priority(),
        event.score(),
        event.occurredAt()));
  }
}
