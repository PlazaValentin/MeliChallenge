package com.hackerrank.challenge.application.service;

import com.hackerrank.challenge.application.output.ScoredQuestion;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.repository.OrderRepository;
import com.hackerrank.challenge.domain.repository.QuestionRepository;
import com.hackerrank.challenge.domain.repository.SellerRepository;
import com.hackerrank.challenge.domain.rules.scoring.QuestionImportanceScorer;
import com.hackerrank.challenge.support.ScoringConfigs;
import com.hackerrank.challenge.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

/**
 * Cubre el orden de la cola de Operaciones, que es lo que esta clase resuelve
 * por su cuenta.
 */
@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

  @Mock
  private QuestionRepository questionRepository;

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private SellerRepository sellerRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private QuestionService questionService;

  @BeforeEach
  void setUp() {
    questionService = new QuestionService(
        questionRepository,
        orderRepository,
        sellerRepository,
        new QuestionImportanceScorer(ScoringConfigs.standard()),
        eventPublisher,
        TestData.clockAt(TestData.NOW));
  }

  @Test
  @DisplayName("la cola ordena las preguntas de mayor a menor importancia")
  void laColaOrdenaLasPreguntasDeMayorAMenorImportancia() {
    Order order = TestData.anOrder().withTotal("620000.00").build();
    Question leve = TestData.aQuestion(order).withText("Consulta menor.").build();
    Question grave = TestData.aQuestion(order)
        .withText("Es urgente, llego roto e incompleto, estoy indignado, esto es una estafa.")
        .build();
    givenUnresolved(order, List.of(leve, grave));

    List<ScoredQuestion> queue = questionService.listUnresolvedQuestions(null);

    assertThat(queue).extracting(scored -> scored.question().getId())
        .containsExactly(grave.getId(), leve.getId());
  }

  /**
   * Ante score igual prevalece la mas antigua. Al ponderar el tiempo por brechas
   * y no por valor absoluto, dos preguntas pueden empatar de verdad: el empate
   * no es solo teorico.
   */
  @Test
  @DisplayName("ante el mismo score, la pregunta mas antigua va primero")
  void anteElMismoScoreLaPreguntaMasAntiguaVaPrimero() {
    Order order = TestData.anOrder().withTotal("620000.00").build();
    Instant older = TestData.NOW.minusSeconds(20 * 3600);
    Instant newer = TestData.NOW.minusSeconds(2 * 3600);

    // Mismo texto y misma brecha de espera (ambas dentro de las primeras 24h):
    // el score es identico y solo las separa la antiguedad.
    Question reciente = TestData.aQuestion(order).withText("Consulta.").createdAt(newer).build();
    Question antigua = TestData.aQuestion(order).withText("Consulta.").createdAt(older).build();
    givenUnresolved(order, List.of(reciente, antigua));

    List<ScoredQuestion> queue = questionService.listUnresolvedQuestions(null);

    assertThat(queue).extracting(scored -> scored.score().total())
        .containsExactly(queue.get(0).score().total(), queue.get(0).score().total());
    assertThat(queue).extracting(scored -> scored.question().getId())
        .containsExactly(antigua.getId(), reciente.getId());
  }

  private void givenUnresolved(Order order, List<Question> questions) {
    given(questionRepository.findUnresolved()).willReturn(questions);
    given(orderRepository.findAllById(anyCollection())).willReturn(List.of(order));
  }
}
