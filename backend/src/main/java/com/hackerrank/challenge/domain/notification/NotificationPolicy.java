package com.hackerrank.challenge.domain.notification;

import com.hackerrank.challenge.domain.exception.DomainValidationException;
import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;

/**
 * Decide si una clasificacion amerita notificar: se avisa desde
 * {@code minimumPriority} hacia arriba.
 *
 * <p>
 * La comparacion se apoya en el orden natural de {@link QuestionPriority}, que
 * va de menos a mas grave; por eso ese orden es significativo y esta advertido
 * en el propio enum.
 */
public record NotificationPolicy(QuestionPriority minimumPriority) {

  public NotificationPolicy {
    if (minimumPriority == null) {
      throw new DomainValidationException("La prioridad minima para notificar es obligatoria.");
    }
  }

  public boolean shouldNotify(QuestionPriority priority) {
    return priority != null && priority.compareTo(minimumPriority) >= 0;
  }
}
