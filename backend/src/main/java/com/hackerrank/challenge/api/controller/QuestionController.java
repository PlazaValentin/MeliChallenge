package com.hackerrank.challenge.api.controller;

import com.hackerrank.challenge.api.dto.AnswerQuestionRequest;
import com.hackerrank.challenge.api.dto.CreateQuestionRequest;
import com.hackerrank.challenge.api.dto.QuestionIdResponse;
import com.hackerrank.challenge.application.service.QuestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Preguntas sobre un pedido y acciones del vendedor sobre ellas.
 *
 * <p>
 * Tiene controller propio por segmentacion de responsabilidades y no queda
 * anidado bajo el vendedor: del lado del vendedor las preguntas ya vienen
 * embebidas en el detalle del pedido, y la cola de Operaciones ya trae el
 * vendedor y el pedido de cada una (ver DECISIONS.md).
 *
 * <p>
 * No lleva {@code @RequestMapping} de clase porque sus rutas cuelgan de dos
 * bases distintas a proposito: la creacion depende del pedido, porque una
 * pregunta no existe sin un pedido al cual pertenecer; las acciones sobre una
 * pregunta ya creada operan sobre su propio id.
 */
@RestController
@Validated
public class QuestionController {

  private final QuestionService questionService;

  public QuestionController(QuestionService questionService) {
    this.questionService = questionService;
  }

  /**
   * Crea una pregunta sobre un pedido.
   *
   * <p>
   * Responde 201 sin header {@code Location}, porque no existe ni se planea un
   * endpoint de detalle de pregunta al cual apuntar: siempre se consultan en el
   * contexto de su pedido o de la cola de Operaciones. Devuelve solo el id, sin
   * clasificacion ni score.
   *
   * <p>
   * Se puede preguntar sobre pedidos en estado terminal: que el pedido este
   * cerrado no significa que el post-venta lo este. Un {@code productId} que no
   * pertenece al pedido responde 400 y no 404, para no revelar que productos
   * tiene el catalogo del vendedor.
   */
  @PostMapping("/api/orders/{orderId}/questions")
  @ResponseStatus(HttpStatus.CREATED)
  public QuestionIdResponse createQuestion(
      @PathVariable @NotNull UUID orderId,
      @RequestBody @Valid CreateQuestionRequest request) {

    return QuestionIdResponse.from(
        questionService.createQuestion(orderId, request.productId(), request.questionText()));
  }

  /**
   * Registra la respuesta del vendedor.
   *
   * <p>
   * Es {@code POST} y no {@code PATCH} porque crea la respuesta, no solo cambia
   * un estado. Una pregunta admite una sola respuesta: responder una ya
   * respondida es una transicion invalida y devuelve 409.
   */
  @PostMapping("/api/questions/{questionId}/answer")
  public QuestionIdResponse answerQuestion(
      @PathVariable @NotNull UUID questionId,
      @RequestBody @Valid AnswerQuestionRequest request) {

    return QuestionIdResponse.from(
        questionService.answerQuestion(questionId, request.answerText()));
  }

  /**
   * Cierre explicito de la pregunta por parte del vendedor.
   *
   * <p>
   * Es {@code PATCH} porque solo cambia un estado, y va sin cuerpo: la accion no
   * necesita ningun dato mas alla de la pregunta sobre la que opera. Responder y
   * resolver son acciones distintas, asi que no se puede saltear de {@code OPEN}
   * a {@code RESOLVED}: intentarlo devuelve 409.
   */
  @PatchMapping("/api/questions/{questionId}/resolve")
  public QuestionIdResponse resolveQuestion(@PathVariable @NotNull UUID questionId) {
    return QuestionIdResponse.from(questionService.resolveQuestion(questionId));
  }
}
