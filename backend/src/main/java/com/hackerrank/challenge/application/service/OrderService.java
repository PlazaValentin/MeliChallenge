package com.hackerrank.challenge.application.service;

import com.hackerrank.challenge.application.input.OrderFilter;
import com.hackerrank.challenge.application.output.OrderAggregates;
import com.hackerrank.challenge.application.output.OrderDetail;
import com.hackerrank.challenge.application.output.SellerOrderSummary;
import com.hackerrank.challenge.domain.BusinessCalendar;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.enums.OrderStatus;
import com.hackerrank.challenge.domain.enums.QuestionStatus;
import com.hackerrank.challenge.domain.exception.ResourceNotFoundException;
import com.hackerrank.challenge.domain.repository.OrderRepository;
import com.hackerrank.challenge.domain.repository.OrderSearchCriteria;
import com.hackerrank.challenge.domain.repository.QuestionRepository;
import com.hackerrank.challenge.domain.repository.SellerRepository;
import com.hackerrank.challenge.domain.rules.scoring.QuestionImportanceScorer;
import com.hackerrank.challenge.domain.rules.scoring.QuestionPriority;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso sobre el agregado {@link Order}.
 *
 * <p>
 * Orquesta, no calcula: el filtrado lo resuelve el repositorio, la validacion
 * de
 * las transiciones vive en {@link OrderStatus} y el score lo provee
 * {@link QuestionImportanceScorer}. Lo unico propio de esta clase es combinar
 * esas piezas y derivar los agregados del pedido.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final QuestionRepository questionRepository;
    private final SellerRepository sellerRepository;
    private final QuestionImportanceScorer scorer;
    private final Clock clock;

    public OrderService(
            OrderRepository orderRepository,
            QuestionRepository questionRepository,
            SellerRepository sellerRepository,
            QuestionImportanceScorer scorer,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.questionRepository = questionRepository;
        this.sellerRepository = sellerRepository;
        this.scorer = scorer;
        this.clock = clock;
    }

    /**
     * Pedidos del vendedor que cumplen los filtros, con sus agregados.
     *
     * <p>
     * Un vendedor inexistente es un 404 y no una lista vacia: el criterio de
     * "id bien formado pero inexistente" aplica por igual a vendedor, pedido y
     * pregunta (ver DECISIONS.md).
     */
    public List<SellerOrderSummary> listSellerOrders(UUID sellerId, OrderFilter filter) {
        requireSellerExists(sellerId);

        OrderFilter effectiveFilter = filter == null ? OrderFilter.none() : filter;
        List<Order> orders = orderRepository.findBySeller(sellerId, toCriteria(effectiveFilter));

        return orders.stream()
                .map(order -> new SellerOrderSummary(order, aggregatesOf(order)))
                .toList();
    }

    /**
     * Pedido con sus preguntas embebidas y los mismos agregados del listado.
     *
     * <p>
     * El pedido se busca dentro del vendedor de la ruta, no de forma global: pedir
     * un pedido del vendedor A por la ruta del vendedor B devuelve 404. No es
     * control de acceso (sigue sin haber login, ver DECISIONS.md) sino coherencia
     * del recurso: si el pedido se resolviera ignorando el {@code sellerId}, la
     * jerarquia de la URL seria decorativa.
     *
     * <p>
     * Las preguntas van en orden cronologico ascendente, al reves que la cola de
     * Operaciones, porque en el detalle se leen como una conversacion.
     */
    public OrderDetail findOrderDetail(UUID sellerId, UUID orderId) {
        Order order = requireOrderOfSeller(sellerId, orderId);
        List<Question> questions = questionRepository.findByOrderId(orderId).stream()
                .sorted(Comparator.comparing(Question::getCreatedAt))
                .toList();

        return new OrderDetail(order, questions, aggregatesOf(order));
    }

    /**
     * Cambia el estado del pedido. La validez de la transicion la decide el propio
     * enum a traves de {@link Order#transitionTo}; aca solo se resuelve el pedido y
     * se persiste el resultado.
     */
    public Order changeStatus(UUID orderId, OrderStatus newStatus) {
        Order order = requireOrder(orderId);
        order.transitionTo(newStatus);
        return orderRepository.save(order);
    }

    /**
     * Expande el rango de fechas a instantes: {@code from} al inicio de su dia y
     * {@code to} al inicio del dia siguiente, cortando el dia en la zona horaria
     * del negocio. Asi el rango queda inclusivo en ambos extremos sin que el
     * repositorio tenga que truncar la hora del campo que filtra.
     */
    private OrderSearchCriteria toCriteria(OrderFilter filter) {
        return new OrderSearchCriteria(
                filter.statuses(),
                startOfDay(filter.from()),
                startOfNextDay(filter.to()),
                filter.buyerText());
    }

    private Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(BusinessCalendar.ZONE_ID).toInstant();
    }

    private Instant startOfNextDay(LocalDate date) {
        return date == null ? null : startOfDay(date.plusDays(1));
    }

    /**
     * Deriva los agregados del pedido a partir de sus preguntas. Se resuelven
     * juntos para recorrer las preguntas una sola vez, aunque el flag y la
     * prioridad miren subconjuntos distintos (ver {@link OrderAggregates}).
     */
    private OrderAggregates aggregatesOf(Order order) {
        List<Question> questions = questionRepository.findByOrderId(order.getId());

        boolean hasPendingQuestions = questions.stream()
                .anyMatch(question -> question.getStatus() == QuestionStatus.OPEN);

        Optional<QuestionPriority> priority = questions.stream()
                .filter(Question::isUnresolved)
                .map(question -> scorer.score(question, order, clock).priority())
                .max(Comparator.naturalOrder());

        return new OrderAggregates(order.getTotalAmount(), hasPendingQuestions, priority);
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("el pedido", orderId));
    }

    /**
     * Un pedido que existe pero pertenece a otro vendedor se trata igual que uno
     * inexistente: mismo 404 y mismo mensaje. Distinguir ambos casos confirmaria la
     * existencia de un pedido ajeno, que es justamente lo que la ruta no expone.
     */
    private Order requireOrderOfSeller(UUID sellerId, UUID orderId) {
        requireSellerExists(sellerId);
        return orderRepository.findById(orderId)
                .filter(order -> order.belongsTo(sellerId))
                .orElseThrow(() -> ResourceNotFoundException.of("el pedido", orderId));
    }

    private void requireSellerExists(UUID sellerId) {
        if (sellerRepository.findById(sellerId).isEmpty()) {
            throw ResourceNotFoundException.of("el vendedor", sellerId);
        }
    }
}
