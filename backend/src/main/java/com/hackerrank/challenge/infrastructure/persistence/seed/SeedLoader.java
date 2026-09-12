package com.hackerrank.challenge.infrastructure.persistence.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackerrank.challenge.domain.entity.Buyer;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.OrderLine;
import com.hackerrank.challenge.domain.entity.Product;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.entity.Seller;
import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.enums.QuestionStatus;
import com.hackerrank.challenge.domain.repository.OrderRepository;
import com.hackerrank.challenge.domain.repository.ProductRepository;
import com.hackerrank.challenge.domain.repository.QuestionRepository;
import com.hackerrank.challenge.domain.repository.SellerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Carga {@code seed/seed-data.json} al arrancar la app y puebla los
 * repositorios
 * in-memory. Cada entidad se construye pasando por el constructor (o factory)
 * del
 * dominio correspondiente, de modo que las mismas invariantes que protegen al
 * API
 * protegen también al dataset de arranque.
 *
 * <p>
 * Se registra como {@link CommandLineRunner} condicionado a la propiedad
 * {@code app.seed.enabled} (ver {@code application.properties}), para poder
 * desactivarlo sin tocar código.
 */
@Configuration
public class SeedLoader {

  private static final String SEED_FILE = "seed/seed-data.json";

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
  public CommandLineRunner seedRunner(
      SellerRepository sellerRepository,
      ProductRepository productRepository,
      OrderRepository orderRepository,
      QuestionRepository questionRepository,
      Clock clock) {
    return args -> load(
        sellerRepository, productRepository, orderRepository, questionRepository, clock);
  }

  private void load(
      SellerRepository sellerRepository,
      ProductRepository productRepository,
      OrderRepository orderRepository,
      QuestionRepository questionRepository,
      Clock clock) throws IOException {

    SeedDataFile data = readSeedFile();
    Instant now = clock.instant();

    data.sellers().forEach(seller -> sellerRepository.save(new Seller(seller.id(), seller.name(), seller.email())));

    data.products().forEach(product -> productRepository.save(
        new Product(product.id(), product.sellerId(), product.name(), product.price())));

    Map<UUID, Order> ordersById = data.orders().stream()
        .map(seedOrder -> buildOrder(seedOrder, now))
        .peek(orderRepository::save)
        .collect(Collectors.toMap(Order::getId, order -> order));

    data.questions().forEach(seedQuestion -> questionRepository.save(buildQuestion(seedQuestion, ordersById, now)));
  }

  private Order buildOrder(SeedDataFile.SeedOrder seedOrder, Instant now) {
    Buyer buyer = new Buyer(seedOrder.buyerName(), seedOrder.buyerEmail());
    List<OrderLine> lines = seedOrder.lines().stream()
        .map(line -> new OrderLine(
            line.productId(), line.productName(), line.quantity(), line.unitPrice()))
        .toList();
    Instant createdAt = now.minus(java.time.Duration.ofDays(seedOrder.offsetDays()));

    Order order = new Order(seedOrder.id(), seedOrder.sellerId(), buyer, lines, createdAt);
    advanceToTargetStatus(order, OrderStatus.valueOf(seedOrder.status()));
    return order;
  }

  /**
   * El pedido nace en PENDING; se lo hace avanzar paso a paso hasta el estado del
   * seed.
   */
  private void advanceToTargetStatus(Order order, OrderStatus target) {
    List<OrderStatus> path = switch (target) {
      case PENDING -> List.of();
      case CONFIRMED -> List.of(OrderStatus.CONFIRMED);
      case SHIPPED -> List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED);
      case DELIVERED -> List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
      case CANCELLED -> List.of(OrderStatus.CANCELLED);
    };
    path.forEach(order::transitionTo);
  }

  private Question buildQuestion(
      SeedDataFile.SeedQuestion seedQuestion, Map<UUID, Order> ordersById, Instant now) {
    Order order = ordersById.get(seedQuestion.orderId());
    Instant createdAt = now.minus(java.time.Duration.ofHours(seedQuestion.offsetHours()));

    Question question = Question.reconstitute(
        seedQuestion.id(), order, seedQuestion.productId(), seedQuestion.text(), createdAt);

    QuestionStatus target = QuestionStatus.valueOf(seedQuestion.status());
    if (target == QuestionStatus.ANSWERED || target == QuestionStatus.RESOLVED) {
      question.answer(seedQuestion.answerText());
    }
    if (target == QuestionStatus.RESOLVED) {
      question.resolve();
    }
    return question;
  }

  private SeedDataFile readSeedFile() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    try (InputStream inputStream = new ClassPathResource(SEED_FILE).getInputStream()) {
      return objectMapper.readValue(inputStream, SeedDataFile.class);
    }
  }
}
