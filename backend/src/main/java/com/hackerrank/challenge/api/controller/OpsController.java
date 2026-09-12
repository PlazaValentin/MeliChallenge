package com.hackerrank.challenge.api.controller;

import com.hackerrank.challenge.api.dto.ListResponse;
import com.hackerrank.challenge.api.dto.UnresolvedQuestionResponse;
import com.hackerrank.challenge.application.service.QuestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Cola de trabajo de Operaciones. Tiene controller propio, separado del de
 * preguntas, porque es otra audiencia con otro proposito y otra pantalla (ver
 * DECISIONS.md).
 */
@RestController
@RequestMapping("/api/ops/questions")
public class OpsController {

  private final QuestionService questionService;

  public OpsController(QuestionService questionService) {
    this.questionService = questionService;
  }

  /**
   * Preguntas sin resolver ({@code OPEN} y {@code ANSWERED}) de todos los
   * vendedores, ordenadas por importancia y, ante empate, la mas antigua primero.
   *
   * <p>
   * Es global por defecto: el {@code sellerId} es un filtro opcional, no parte de
   * la ruta, porque esta pantalla existe justamente para cruzar vendedores.
   * Filtrar por un vendedor inexistente responde 404, igual que en el resto de la
   * API.
   */
  @GetMapping("/unresolved")
  public ListResponse<UnresolvedQuestionResponse> listUnresolved(
      @RequestParam(required = false) UUID sellerId) {
    // Sin anotaciones de validacion: el unico parametro es opcional y su formato
    // ya lo garantiza la conversion a UUID que hace Spring antes de llegar aca.
    
    List<UnresolvedQuestionResponse> items =

                    .map(UnresolvedQuestionR         .toList();
        
        nse.of(ite

    
  