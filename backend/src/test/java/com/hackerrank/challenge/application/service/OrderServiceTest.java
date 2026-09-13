package com.hackerrank.challenge.application.service;

import com.hackerrank.challenge.application.output.OrderAggregates;
import com.hackerrank.challenge.application.output.SellerOrderSummary;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.entity.Seller;
import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.repository.OrderRepository;
import com.hackerrank.challenge.domain.repository.OrderSearchCriteria;
import com.hackerrank.challenge.domain.repository.QuestionRepository;
import com.hackerrank.challenge.domain.repository.SellerRepository;
import com.hackerrank.challenge.domain.rules.scoring.QuestionImportanceScorer;
import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;
import com.hackerrank.challenge.support.ScoringConfigs;
import com.hackerrank.challenge.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Cubre la derivacion de los agregados del pedido, que es lo unico que esta
 * clase calcula por su cuenta: el filtrado lo resuelve el repositorio y las
 * transiciones el propio enum.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private QuestionRepository questionRepository;

  @Mock
  private SellerRepository sellerRepository;

  private OrderService orderService;
  private final UUID sellerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    orderService = new OrderService(
        orderRepository,
        questionRepository,
        sellerRepository,
        new QuestionImportanceScorer(ScoringConfigs.standard()),
        TestData.clockAt(TestData.NOW));

    given(sellerRepository.findById(sellerId))
        .willReturn(Optional.of(new Seller(sellerId, "Vendedor", "vendedor@test.com")));
  }

  /**
   * La prioridad del pedido es el maximo de las de sus preguntas sin resolver:
   * sumarlas haria que varias consultas triviales parecieran mas graves que una
   * sola realmente critica.
   */
  @Test
  @DisplayName("la prioridad del pedido es la mas alta de sus preguntas sin resolver")
  void laPrioridadDelPedidoEsLaMasAltaDeSusPreguntasSinResolver() {
    Order order = orderOf("620000.00", OrderStatus.PENDING);
    Question trivial = TestData.aQuestion(order)
        .withText("Consulta por la fecha de entrega.")
        .build();
    Question grave = TestData.aQuestion(order)
        .withText("Es urgente, llego roto e incompleto, estoy indignado, esto es una estafa.")
        .build();
    givenOrderWithQuestions(order, List.of(trivial, grave));

    assertThat(priorityOf(order)).contains(QuestionPriority.HIGH);
  }

  /**
   * Si los scores se sumaran, tres preguntas LOW escalarian el pedido a una
   * categoria superior sin que ninguna lo amerite.
   */
  @Test
  @DisplayName("varias preguntas leves no escalan la prioridad del pedido")
  void variasPreguntasLevesNoEscalanLaPrioridadDelPedido() {
    Order order = orderOf("10000.00", OrderStatus.DELIVERED);
    List<Question> questions = List.of(
        TestData.aQuestion(order).withText("Consulta uno.").build(),
        TestData.aQuestion(order).withText("Consulta dos.").build(),
        TestData.aQuestion(order).withText("Consulta tres.").build());
    givenOrderWithQuestions(order, questions);

    assertThat(priorityOf(order)).contains(QuestionPriority.LOW);
  }

  @Test
  @DisplayName("un pedido sin preguntas no tiene prioridad, que es distinto de tenerla baja")
  void unPedidoSinPreguntasNoTienePrioridad() {
    Order order = orderOf("10000.00", OrderStatus.PENDING);
    givenOrderWithQuestions(order, List.of());

    assertThat(priorityOf(order)).isEmpty();
  }

  @Test
  @DisplayName("un pedido con todas sus preguntas resueltas no tiene prioridad")
  void unPedidoConTodasSusPreguntasResueltasNoTienePrioridad() {
    Order order = orderOf("620000.00", OrderStatus.CANCELLED);
    givenOrderWithQuestions(order, List.of(TestData.aQuestion(order).resolved().build()));

    assertThat(priorityOf(order)).isEmpty();
  }

  @Test
  @DisplayName("las preguntas resueltas no influyen en la prioridad del pedido")
  void lasPreguntasResueltasNoInfluyenEnLaPrioridadDelPedido() {
    Order order = orderOf("620000.00", OrderStatus.CANCELLED);
    Question graveResuelta = TestData.aQuestion(order)
        .withText("Es urgente, llego roto e incompleto, estoy indignado, esto es una estafa.")
        .resolved()
        .build();
    Question leveAbierta = TestData.aQuestion(order)
        .withText("Consulta menor.")
        .build();
    givenOrderWithQuestions(order, List.of(graveResuelta, leveAbierta));

    assertThat(priorityOf(order)).contains(QuestionPriority.MEDIUM);
  }

  @Test
  @DisplayName("el flag de pendientes se enciende cuando hay preguntas sin responder")
  void elFlagDePendientesSeEnciendeCuandoHayPreguntasSinResponder() {
    Order order = orderOf("10000.00", OrderStatus.PENDING);
    givenOrderWithQuestions(order, List.of(TestData.aQuestion(order).build()));

    assertThat(aggregatesOf(order).hasPendingQuestions()).isTrue();
  }

  /**
   * El flag y la prioridad no miran el mismo conjunto: el flag es
   * "respondiste / no respondiste" y solo mira las abiertas, mientras que la
   * prioridad abarca todas las sin resolver.
   */
  @Test
  @DisplayName("con todas respondidas y ninguna resuelta el flag se apaga pero queda prioridad")
  void conTodasRespondidasYNingunaResueltaElFlagSeApagaPeroQuedaPrioridad() {
    Order order = orderOf("620000.00", OrderStatus.CANCELLED);
    givenOrderWithQuestions(order, List.of(TestData.aQuestion(order).answered().build()));

    OrderAggregates aggregates = aggregatesOf(order);

    assertThat(aggregates.hasPendingQuestions()).isFalse();
    assertThat(aggregates.priority()).isPresent();
  }

  @Test
  @DisplayName("el listado expone el total del pedido")
  void elListadoExponeElTotalDelPedido() {
    Order order = orderOf("620000.00", OrderStatus.PENDING);
    givenOrderWithQuestions(order, List.of());

    assertThat(aggregatesOf(order).totalAmount()).isEqualByComparingTo("620000.00");
  }

  private Order orderOf(String total, OrderStatus status) {
    return TestData.anOrder()
        .withSellerId(sellerId)
        .withTotal(total)
        .withStatus(status)
        .build();
  }

  private void givenOrderWithQuestions(Order order, List<Question> questions) {
    given(orderRepository.findBySeller(any(UUID.class), any(OrderSearchCriteria.class)))
        .willReturn(List.of(order));
    given(questionRepository.findByOrderId(order.getId())).willReturn(questions);
  }

  private Optional<QuestionPriority> priorityOf(Order order) {
    return aggregatesOf(order).priority();
  }

  private OrderAggregates aggregatesOf(Order order) {
    List<SellerOrderSummary> summaries = orderService.listSellerOrders(sellerId, null);
    assertThat(summaries).hasSize(1);
    return summaries.get(0).aggregates();
  }
}
