package com.hackerrank.challenge.infrastructure.config;

import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lee {@code app.notifications.*}. El estado de cada canal no se lee aca: cada
 * canal se activa solo con {@code @ConditionalOnProperty}, para que sumar uno
 * nuevo no obligue a tocar esta clase.
 */
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

  private QuestionPriority minimumPriority;

  public QuestionPriority getMinimumPriority() {
    return minimumPriority;
  }

  public void setMinimumPriority(QuestionPriority minimumPriority) {
    this.minimumPriority = minimumPriority;
  }
}
