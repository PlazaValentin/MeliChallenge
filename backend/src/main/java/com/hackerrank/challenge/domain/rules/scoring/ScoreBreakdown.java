package com.hackerrank.challenge.domain.rules.scoring;

/**
 * Aporte de cada factor al score total de una pregunta. Se expone completo (no
 * solo el total) porque Operaciones necesita entender por qué una pregunta
 * quedó
 * arriba de otra, para poder ayudar a ajustar la fórmula (ver DECISIONS.md).
 */
public record ScoreBreakdown(
    int waitingTimePoints,
    int keywordPoints,
    int orderAmountPoints,
    int orderStatusPoints,
    int questionStatusPoints) {

  public int total() {
    return waitingTimePoints + keywordPoints + orderAmountPoints
        + orderStatusPoints + questionStatusPoints;
  }
}
