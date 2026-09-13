package com.hackerrank.challenge.domain.rules.scoring;

import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.exception.DomainValidationException;
import com.hackerrank.challenge.support.ScoringConfigs;
import com.hackerrank.challenge.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El reloj siempre es fijo: el factor tiempo es sensible al instante de
 * evaluacion y con el reloj real estos tests serian flaky.
 */
class QuestionImportanceScorerTest {

  private final QuestionImportanceScorer scorer = new QuestionImportanceScorer(ScoringConfigs.standard());

  @Nested
  @DisplayName("Tiempo de espera")
  class WaitingTime {

    @ParameterizedTest(name = "{0} horas de espera aportan {1} puntos")
    @CsvSource({
        "0, 0",
        "23, 0",
        "24, 0",
        "25, 20",
        "72, 20",
        "73, 40",
        "168, 40",
        "169, 60",
        "720, 60"
    })
    void puntuaSegunLaBrechaEnLaQueCaeLaEspera(long hoursWaiting, int expectedPoints) {
      Order order = TestData.anOrder().build();
      Question question = TestData.aQuestion(order).createdAt(TestData.NOW).build();

      int points = scorer
          .score(question, order, TestData.clockHoursAfterNow(hoursWaiting))
          .breakdown()
          .waitingTimePoints();

      assertThat(points).isEqualTo(expectedPoints);
    }

    @Test
    @DisplayName("una pregunta con fecha futura no puntua espera negativa")
    void unaPreguntaConFechaFuturaNoPuntuaEsperaNegativa() {
      Order order = TestData.anOrder().build();
      Question question = TestData.aQuestion(order)
          .createdAt(TestData.NOW.plusSeconds(3600))
          .build();

      int points = scorer.score(question, order, TestData.clockAt(TestData.NOW))
          .breakdown()
          .waitingTimePoints();

      assertThat(points).isZero();
    }
  }

  @Nested
  @DisplayName("Palabras clave")
  class Keywords {

    @Test
    @DisplayName("una palabra clave repetida no vuelve a sumar")
    void unaPalabraClaveRepetidaNoVuelveASumar() {
      assertThat(keywordPointsOf("Esto es urgente, urgente y urgente.")).isEqualTo(10);
    }

    @Test
    @DisplayName("cada palabra clave distinta suma por separado")
    void cadaPalabraClaveDistintaSumaPorSeparado() {
      assertThat(keywordPointsOf("Llego roto e incompleto, es urgente.")).isEqualTo(30);
    }

    @Test
    @DisplayName("las palabras clave se detectan sin importar mayusculas")
    void lasPalabrasClaveSeDetectanSinImportarMayusculas() {
      assertThat(keywordPointsOf("Esto es URGENTE.")).isEqualTo(10);
    }

    @Test
    @DisplayName("una palabra clave contenida dentro de otra palabra no cuenta")
    void unaPalabraClaveContenidaDentroDeOtraPalabraNoCuenta() {
      assertThat(keywordPointsOf("El producto es un prototipo.")).isZero();
    }

    @Test
    @DisplayName("un texto sin palabras clave no suma")
    void unTextoSinPalabrasClaveNoSuma() {
      assertThat(keywordPointsOf("Queria consultar por la fecha de entrega.")).isZero();
    }

    /**
     * Solo se mira el texto de la pregunta: si la respuesta contara, el vendedor
     * podria alterar el score de la pregunta que le toca atender.
     */
    @Test
    @DisplayName("las palabras clave de la respuesta del vendedor no alteran el score")
    void lasPalabrasClaveDeLaRespuestaDelVendedorNoAlteranElScore() {
      Order order = TestData.anOrder().build();
      Question question = TestData.aQuestion(order)
          .withText("Queria consultar por la fecha de entrega.")
          .build();

      int beforeAnswer = keywordPointsOf(question, order);
      question.answer("Es urgente, ya despachamos el pedido roto.");
      int afterAnswer = keywordPointsOf(question, order);

      assertThat(beforeAnswer).isZero();
      assertThat(afterAnswer).isZero();
    }

    private int keywordPointsOf(String text) {
      Order order = TestData.anOrder().build();
      return keywordPointsOf(TestData.aQuestion(order).withText(text).build(), order);
    }

    private int keywordPointsOf(Question question, Order order) {
      return scorer.score(question, order, TestData.clockAt(TestData.NOW))
          .breakdown()
          .keywordPoints();
    }
  }

  @Nested
  @DisplayName("Monto del pedido")
  class OrderAmount {

    @ParameterizedTest(name = "un pedido de {0} aporta {1} puntos")
    @CsvSource({
        "0.00, 0",
        "49999.99, 0",
        "50000.00, 15",
        "149999.99, 15",
        "150000.00, 25",
        "499999.99, 25",
        "500000.00, 40",
        "1200000.00, 40"
    })
    void puntuaSegunLaBrechaEnLaQueCaeElTotal(String total, int expectedPoints) {
      Order order = TestData.anOrder().withTotal(total).build();
      Question question = TestData.aQuestion(order).build();

      int points = scorer.score(question, order, TestData.clockAt(TestData.NOW))
          .breakdown()
          .orderAmountPoints();

      assertThat(points).isEqualTo(expectedPoints);
    }
  }

  @Nested
  @DisplayName("Estado del pedido")
  class OrderStatusFactor {

    @ParameterizedTest(name = "un pedido {0} aporta {1} puntos")
    @CsvSource({
        "PENDING, 10",
        "CONFIRMED, 10",
        "SHIPPED, 5",
        "DELIVERED, 0",
        "CANCELLED, 30"
    })
    void puntuaSegunElEstadoDelPedido(OrderStatus status, int expectedPoints) {
      Order order = TestData.anOrder().withStatus(status).build();
      Question question = TestData.aQuestion(order).build();

      int points = scorer.score(question, order, TestData.clockAt(TestData.NOW))
          .breakdown()
          .orderStatusPoints();

      assertThat(points).isEqualTo(expectedPoints);
    }

    /**
     * Una pregunta sobre un pedido cancelado suele implicar devolucion de
     * dinero: es mas sensible que una sobre un pedido que todavia se corrige.
     */
    @Test
    @DisplayName("un pedido cancelado pondera mas que uno en curso")
    void unPedidoCanceladoPonderaMasQueUnoEnCurso() {
      assertThat(orderStatusPointsOf(OrderStatus.CANCELLED))
          .isGreaterThan(orderStatusPointsOf(OrderStatus.PENDING))
          .isGreaterThan(orderStatusPointsOf(OrderStatus.CONFIRMED))
          .isGreaterThan(orderStatusPointsOf(OrderStatus.SHIPPED));
    }

    private int orderStatusPointsOf(OrderStatus status) {
      Order order = TestData.anOrder().withStatus(status).build();
      return scorer.score(TestData.aQuestion(order).build(), order, TestData.clockAt(TestData.NOW))
          .breakdown()
          .orderStatusPoints();
    }
  }

  @Nested
  @DisplayName("Estado de la pregunta")
  class QuestionStatusFactor {

    private final Order order = TestData.anOrder().build();

    @Test
    @DisplayName("una pregunta abierta aporta el maximo de su factor")
    void unaPreguntaAbiertaAportaElMaximoDeSuFactor() {
      assertThat(questionStatusPointsOf(TestData.aQuestion(order).build())).isEqualTo(15);
    }

    @Test
    @DisplayName("una pregunta respondida aporta menos que una abierta")
    void unaPreguntaRespondidaAportaMenosQueUnaAbierta() {
      assertThat(questionStatusPointsOf(TestData.aQuestion(order).answered().build()))
          .isEqualTo(5);
    }

    @Test
    @DisplayName("una pregunta resuelta no aporta puntos por su estado")
    void unaPreguntaResueltaNoAportaPuntosPorSuEstado() {
      assertThat(questionStatusPointsOf(TestData.aQuestion(order).resolved().build()))
          .isZero();
    }

    private int questionStatusPointsOf(Question question) {
      return scorer.score(question, order, TestData.clockAt(TestData.NOW))
          .breakdown()
          .questionStatusPoints();
    }
  }

  @Nested
  @DisplayName("Clasificacion")
  class Classification {

    /**
     * Se usa una config de un solo factor: con los puntajes reales, todos
     * multiplos de cinco, los bordes exactos (49, 89, 129) no son alcanzables.
     */
    @ParameterizedTest(name = "un score de {0} clasifica como {1}")
    @CsvSource({
        "0, LOW",
        "49, LOW",
        "50, MEDIUM",
        "89, MEDIUM",
        "90, HIGH",
        "129, HIGH",
        "130, CRITICAL",
        "195, CRITICAL"
    })
    void clasificaSegunElUmbralQueAlcanzaElTotal(int total, QuestionPriority expected) {
      QuestionImportanceScorer singleFactorScorer = new QuestionImportanceScorer(ScoringConfigs.onlyKeyword(total));
      Order order = TestData.anOrder().build();
      Question question = TestData.aQuestion(order)
          .withText("Texto con " + ScoringConfigs.SINGLE_KEYWORD + " adentro.")
          .build();

      QuestionScore score = singleFactorScorer.score(question, order, TestData.clockAt(TestData.NOW));

      assertThat(score.total()).isEqualTo(total);
      assertThat(score.priority()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("Score total")
  class Total {

    @Test
    @DisplayName("el total es la suma de los cinco factores")
    void elTotalEsLaSumaDeLosCincoFactores() {
      Order order = TestData.anOrder()
          .withTotal("620000.00")
          .withStatus(OrderStatus.CANCELLED)
          .build();
      Question question = TestData.aQuestion(order)
          .withText("Es urgente, llego roto e incompleto, estoy indignado, esto es una estafa.")
          .build();

      QuestionScore score = scorer.score(question, order, TestData.clockHoursAfterNow(200));
      ScoreBreakdown breakdown = score.breakdown();

      assertThat(breakdown.waitingTimePoints()).isEqualTo(60);
      assertThat(breakdown.keywordPoints()).isEqualTo(50);
      assertThat(breakdown.orderAmountPoints()).isEqualTo(40);
      assertThat(breakdown.orderStatusPoints()).isEqualTo(30);
      assertThat(breakdown.questionStatusPoints()).isEqualTo(15);
      assertThat(score.total()).isEqualTo(195);
    }

    /**
     * Al crear, el factor tiempo aporta cero y el techo depende del estado del
     * pedido: si el umbral de notificacion (HIGH) no fuera alcanzable en ese
     * momento, toda pregunta grave sobre un pedido vigente quedaria sin aviso.
     */
    @Test
    @DisplayName("el umbral de notificacion es alcanzable al crear la pregunta")
    void elUmbralDeNotificacionEsAlcanzableAlCrearLaPregunta() {
      Order order = TestData.anOrder().withTotal("620000.00").build();
      Question question = TestData.aQuestion(order)
          .withText("Es urgente, llego roto e incompleto, estoy indignado, esto es una estafa.")
          .build();

      QuestionScore score = scorer.score(question, order, TestData.clockAt(question.getCreatedAt()));

      // 40 (monto) + 50 (cinco keywords) + 10 (PENDING) + 15 (OPEN): el techo
      // sobre un pedido en curso, sin el factor tiempo.
      assertThat(score.breakdown().waitingTimePoints()).isZero();
      assertThat(score.total()).isEqualTo(115);
      assertThat(score.priority()).isEqualTo(QuestionPriority.HIGH);
    }

    /**
     * Contracara del caso anterior: al crear, CRITICAL solo se alcanza si el
     * pedido esta cancelado, que es lo que justifica fijar el umbral de
     * notificacion en HIGH y no en CRITICAL.
     */
    @Test
    @DisplayName("al crear, solo un pedido cancelado alcanza la clasificacion critica")
    void alCrearSoloUnPedidoCanceladoAlcanzaLaClasificacionCritica() {
      Order order = TestData.anOrder()
          .withTotal("620000.00")
          .withStatus(OrderStatus.CANCELLED)
          .build();
      Question question = TestData.aQuestion(order)
          .withText("Es urgente, llego roto e incompleto, estoy indignado, esto es una estafa.")
          .build();

      QuestionScore score = scorer.score(question, order, TestData.clockAt(question.getCreatedAt()));

      assertThat(score.total()).isEqualTo(135);
      assertThat(score.priority()).isEqualTo(QuestionPriority.CRITICAL);
    }
  }

  @Nested
  @DisplayName("Argumentos obligatorios")
  class RequiredArguments {

    @Test
    void rechazaPuntuarSinPregunta() {
      assertThatThrownBy(() -> scorer.score(null, TestData.anOrder().build(), fixedClock()))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rechazaPuntuarSinPedido() {
      Order order = TestData.anOrder().build();
      assertThatThrownBy(() -> scorer.score(TestData.aQuestion(order).build(), null, fixedClock()))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rechazaPuntuarSinReloj() {
      Order order = TestData.anOrder().build();
      assertThatThrownBy(() -> scorer.score(TestData.aQuestion(order).build(), order, null))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rechazaConstruirseSinConfiguracion() {
      assertThatThrownBy(() -> new QuestionImportanceScorer(null))
          .isInstanceOf(DomainValidationException.class);
    }

    private Clock fixedClock() {
      return TestData.clockAt(TestData.NOW);
    }
  }
}
