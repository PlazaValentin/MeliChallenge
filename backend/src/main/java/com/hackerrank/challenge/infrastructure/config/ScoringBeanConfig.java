package com.hackerrank.challenge.infrastructure.config;

import com.hackerrank.challenge.domain.rules.scoring.QuestionImportanceScorer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arma el {@link QuestionImportanceScorer} del dominio a partir de
 * {@link ScoringProperties}. Es el único lugar donde la configuración leída de
 * {@code application.properties} cruza hacia el dominio.
 */
@Configuration
@EnableConfigurationProperties(ScoringProperties.class)
public class ScoringBeanConfig {

  @Bean
  public QuestionImportanceScorer questionImportanceScorer(ScoringProperties properties) {
    return new QuestionImportanceScorer(properties.toScoringConfig());
  }
}
