package com.hackerrank.challenge.domain.rules.scoring;

/**
 * Resultado del scoring: el desglose por factor y la clasificación derivada de
 * su total.
 */
public record QuestionScore(ScoreBreakdown breakdown, QuestionPriority priority) {

  public int total() {
    return breakdown.total();
  }
}
