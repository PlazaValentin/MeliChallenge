package com.hackerrank.challenge.support;

import com.hackerrank.challenge.domain.entity.Buyer;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.OrderLine;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Builders de entidades con valores por defecto validos: cada test sobreescribe
 * solo lo que le importa y el resto queda implicito.
 *
 * <p>
 * Los tests arman sus propios datos y no usan el seed: el seed existe para la
 * demo, y acoplarse a el haria que un cambio de dataset rompa tests sin razon.
 */
public final class TestData {

  /** Instante fijo: donde el tiempo interviene, no debe venir del reloj real. */
  public static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

  private TestData() {
  }

  public static Clock clockAt(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  /** Reloj adelantado respecto de {@link #NOW}, para envejecer una pregunta. */
  public static Clock clockHoursAfterNow(long hours) {
    return clockAt(NOW.plusSeconds(hours * 3600));
  }

  public static OrderBuilder anOrder() {
    return new OrderBuilder();
  }

  public static QuestionBuilder aQuestion(Order order) {
    return new QuestionBuilder(order);
  }

  public static final class OrderBuilder {

    private UUID id = UUID.randomUUID();
    private UUID sellerId = UUID.randomUUID();
    private Buyer buyer = new Buyer("Comprador de prueba", "comprador@test.com");
    private List<OrderLine> lines = List.of(line("10000.00", 1));
    private Instant createdAt = NOW;
    private OrderStatus status = OrderStatus.PENDING;

    public OrderBuilder withId(UUID value) {
      this.id = value;
      return this;
    }

    public OrderBuilder withSellerId(UUID value) {
      this.sellerId = value;
      return this;
    }

    public OrderBuilder withBuyer(String name, String email) {
      this.buyer = new Buyer(name, email);
      return this;
    }

    public OrderBuilder withLines(List<OrderLine> value) {
      this.lines = value;
      return this;
    }

    /** Atajo para fijar el total del pedido, que es lo que pondera el scoring. */
    public OrderBuilder withTotal(String amount) {
      this.lines = List.of(line(amount, 1));
      return this;
    }

    public OrderBuilder createdAt(Instant value) {
      this.createdAt = value;
      return this;
    }

    /**
     * Lleva el pedido al estado pedido recorriendo el ciclo de vida: no hay
     * forma de construirlo directamente en un estado que no sea PENDING, y eso
     * es una invariante, no un obstaculo a saltear.
     */
    public OrderBuilder withStatus(OrderStatus value) {
      this.status = value;
      return this;
    }

    public Order build() {
      Order order = new Order(id, sellerId, buyer, lines, createdAt);
      pathTo(status).forEach(order::transitionTo);
      return order;
    }

    private static List<OrderStatus> pathTo(OrderStatus target) {
      return switch (target) {
        case PENDING -> List.of();
        case CONFIRMED -> List.of(OrderStatus.CONFIRMED);
        case SHIPPED -> List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED);
        case DELIVERED -> List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        case CANCELLED -> List.of(OrderStatus.CANCELLED);
      };
    }
  }

  public static final class QuestionBuilder {

    private final Order order;
    private UUID id = UUID.randomUUID();
    private UUID productId;
    private String text = "Consulta de prueba sobre el pedido.";
    private Instant createdAt = NOW;
    private boolean answered;
    private boolean resolved;

    private QuestionBuilder(Order order) {
      this.order = order;
    }

    public QuestionBuilder withId(UUID value) {
      this.id = value;
      return this;
    }

    public QuestionBuilder about(UUID value) {
      this.productId = value;
      return this;
    }

    public QuestionBuilder withText(String value) {
      this.text = value;
      return this;
    }

    public QuestionBuilder createdAt(Instant value) {
      this.createdAt = value;
      return this;
    }

    public QuestionBuilder answered() {
      this.answered = true;
      return this;
    }

    public QuestionBuilder resolved() {
      this.answered = true;
      this.resolved = true;
      return this;
    }

    public Question build() {
      Question question = Question.reconstitute(id, order, productId, text, createdAt);
      if (answered) {
        question.answer("Respuesta de prueba.");
      }
      if (resolved) {
        question.resolve();
      }
      return question;
    }
  }

  public static OrderLine line(String unitPrice, int quantity) {
    return new OrderLine(UUID.randomUUID(), "Producto de prueba", quantity, new BigDecimal(unitPrice));
  }
}
