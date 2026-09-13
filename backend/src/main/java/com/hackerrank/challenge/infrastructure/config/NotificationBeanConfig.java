package com.hackerrank.challenge.infrastructure.config;

import com.hackerrank.challenge.domain.notification.NotificationPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Arma la {@link NotificationPolicy} del dominio a partir de
 * {@link NotificationProperties} y habilita la ejecucion asincronica que usa el
 * listener de preguntas creadas.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationBeanConfig {

  @Bean
  public NotificationPolicy notificationPolicy(NotificationProperties properties) {
    return new NotificationPolicy(properties.getMinimumPriority());
  }
}
