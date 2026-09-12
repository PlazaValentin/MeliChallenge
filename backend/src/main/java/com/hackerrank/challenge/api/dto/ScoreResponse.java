package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;
import com.hackerrank.challenge.domain.rules.scoring.QuestionScore;
import com.hackerrank.challenge.domain.rules.scoring.ScoreBreakdown;

/**
 * Score de una pregunta con su desglose por factor.
 *
 * <p>
 * El desglose se expone completo (no solo el total) porque Operaciones necesita
 * entender por que una pregunta quedo arriba de otra, para poder ayudar a
 * ajustar la formula (ver DECISIONS.md).
 *
 * <p>
 * Solo aparece en la cola de Operaciones: el score se calcula al vuelo en cada
 * consulta y no se persiste, asi que no es un atributo de la pregunta.
 */
public record ScoreResponse(
        int total,
        QuestionPriority priority,
        BreakdownResponse breakdown) {

    public record BreakdownResponse(
            int waitingTimePoints,
            int keywordPoints,
            int orderAmountPoints,
            int orderStatusPoints,
            int questionStatusPoints) {

        static BreakdownResponse from(ScoreBreakdown breakdown) {
            return new BreakdownResponse(
                    breakdown.waitingTimePoints(),
                    breakdown.keywordPoints(),
                    breakdown.orderAmountPoints(),
                    breakdown.orderStatusPoints(),
                    breakdown.questionStatusPoints());
        }
    }

    public static ScoreResponse from(QuestionScore score) {
        return new ScoreResponse(
                score.total(),
                score.priority(),
                BreakdownResponse.from(score.breakdown()));
    }
}
