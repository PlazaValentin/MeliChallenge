package com.hackerrank.challenge.infrastructure.config;

import com.hackerrank.challenge.domain.BusinessCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj único de la aplicación, en la zona horaria del negocio. Lo usa el seed
 * para calcular fechas relativas al arranque, y el scoring al vuelo (ver
 * {@code QuestionImportanceScorer#score}).
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.system(BusinessCalendar.ZONE_ID);
  }
}
