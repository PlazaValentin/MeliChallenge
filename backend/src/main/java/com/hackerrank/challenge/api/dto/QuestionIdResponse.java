package com.hackerrank.challenge.api.dto;

import com.hackerrank.challenge.domain.entity.Question;

import java.util.UUID;

/**
 * Respuesta de la creacion de una pregunta y de las acciones que cambian su
 * estado.
 *
 * <p>
 * No lleva score ni clasificacion: quien crea la pregunta es el comprador y no
 * corresponde que sepa con que criticidad se clasifico su propia consulta.
 * Ademas el score no se persiste, asi que devolverlo aca expondria un calculo
 * puntual como si fuera un atributo de la entidad (ver DECISIONS.md).
 */
public record QuestionIdResponse(UUID questionId) {

  public static QuestionIdResponse from(Question question) {
    return new QuestionIdResponse(question.getId());
  }
}
