package com.hackerrank.challenge.application.service;

import com.hackerrank.challenge.application.output.ScoredQuestion;
import com.hackerrank.challenge.domain.entity.Order;
import com.hackerrank.challenge.domain.entity.Question;
import com.hackerrank.challenge.domain.exception.ResourceNotFoundException;
import com.hackerrank.challenge.domain.repository.OrderRepository;
import com.hackerrank.challenge.domain.repository.OrderSearchCriteria;
import com.hackerrank.challenge.domain.repository.QuestionRepository;
import com.hackerrank.challenge.domain.repository.SellerRepository;
import com.hackerrank.challenge.domain.rules.scoring.QuestionImportanceScorer;
import com.hackerrank.challenge.domain.rules.scoring.QuestionScore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Casos de uso sobre el agregado {@link Question}.
 *
 * <p>
 * Orquesta, no calcula: que el producto referenciado pertenezca al pedido lo
 * valida {@link Question#on}, las transiciones las validan los metodos de
 * negocio de la entidad y el score lo provee {@link QuestionImportanceScorer}.
 */
@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final OrderRepository orderRepository;
    private final SellerRepository sellerRepository;
    private final QuestionImportanceScorer scorer;
    private final Clock clock;

    public QuestionService(
            QuestionRepository questionRepository,
            OrderRepository orderRepository,
            SellerRepository sellerRepository,
            QuestionImportanceScorer scorer,
            Clock clock) {
        this.questionRepository = questionRepository;
        this.orderRepository = orderRepository;
        this.sellerRepository = sellerRepository;
        this.scorer = scorer;
        this.clock = clock;
    }

    /**
     * Crea una pregunta sobre un pedido.
     *
     * <p>
     * El pedido se resuelve aca porque la factory lo necesita para verificar que el
     * producto referenciado le pertenezca; esa validacion no puede hacerse teniendo
     * solo el {@code orderId}.
     *
     * <p>
     * No se calcula el score en este punto: el score al crear solo sirve para
     * decidir si se notifica, y las notificaciones son una etapa aparte.
     */
    public Question createQuestion(UUID orderId, UUID productId, String questionText) {
        Order order = requireOrder(orderId);
        Question question = Question.on(order, productId, questionText, clock.instant());
        return questionRepository.save(question);
    }

    public Question answerQuestion(UUID questionId, String answerText) {
        Question question = requireQuestion(questionId);
        question.answer(answerText);
        return questionRepository.save(question);
    }

    public Question resolveQuestion(UUID questionId) {
        Question question = requireQuestion(questionId);
        question.resolve();
        return questionRepository.save(question);
    }

    /**
     * Cola de Operaciones: preguntas sin resolver con su score recalculado al
     * vuelo, de mayor a menor importancia y, ante empate, la mas antigua primero.
     *
     * <p>
     * El filtro por vendedor se orquesta aca y no en el repositorio de preguntas
     * porque {@link Question} solo conoce su pedido, no el vendedor de ese pedido.
     *
     * @param sellerId nulo para traer la cola global, de todos los vendedores.
     */
    public List<ScoredQuestion> listUnresolvedQuestions(UUID sellerId) {
        List<Question> unresolved = questionRepository.findUnresolved();
        Map<UUID, Order> ordersById = resolveOrders(sellerId, unresolved);

        return unresolved.stream()
                .filter(question -> ordersById.containsKey(question.getOrderId()))
                .map(question -> toScoredQuestion(question, ordersById.get(question.getOrderId())))
                .sorted(byImportance())
                .toList();
    }

    /**
     * Pedidos contra los cuales se puntuan las preguntas. Sin filtro se traen los
     * pedidos referenciados por las preguntas en una sola llamada; con filtro se
     * traen todos los del vendedor y la pertenencia se decide por presencia en el
     * mapa.
     */
    private Map<UUID, Order> resolveOrders(UUID sellerId, List<Question> unresolved) {
        if (sellerId != null) {
            requireSellerExists(sellerId);
            return indexById(orderRepository.findBySeller(sellerId, OrderSearchCriteria.none()));
        }

        Set<UUID> orderIds = unresolved.stream()
                .map(Question::getOrderId)
                .collect(Collectors.toSet());

        Map<UUID, Order> ordersById = indexById(orderRepository.findAllById(orderIds));
        requireNoOrphanQuestions(orderIds, ordersById);
        return ordersById;
    }

    /**
     * Una pregunta que referencia un pedido inexistente es una violacion de
     * integridad, no un caso de uso: no se filtra en silencio ni se traduce a un
     * 404
     * (seria atribuirle al cliente un problema que es del sistema). Se corta con un
     * error no contemplado, que el handler centralizado mapea a 500.
     */
    private void requireNoOrphanQuestions(Set<UUID> referencedIds, Map<UUID, Order> resolved) {
        Set<UUID> missing = referencedIds.stream()
                .filter(id -> !resolved.containsKey(id))
                .collect(Collectors.toSet());

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Hay preguntas que referencian pedidos inexistentes: " + missing + ".");
        }
    }

    private Map<UUID, Order> indexById(List<Order> orders) {
        return orders.stream().collect(Collectors.toMap(Order::getId, Function.identity()));
    }

    private ScoredQuestion toScoredQuestion(Question question, Order order) {
        QuestionScore score = scorer.score(question, order, clock);
        return new ScoredQuestion(question, order, score);
    }

    /** Score descendente; ante score igual, prevalece la pregunta mas antigua. */
    private Comparator<ScoredQuestion> byImportance() {
        return Comparator
                .comparingInt((ScoredQuestion scored) -> scored.score().total()).reversed()
                .thenComparing(scored -> scored.question().getCreatedAt());
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("el pedido", orderId));
    }

    private Question requireQuestion(UUID questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> ResourceNotFoundException.of("la pregunta", questionId));
    }

    private void requireSellerExists(UUID sellerId) {
        if (sellerRepository.findById(sellerId).isEmpty()) {
            throw ResourceNotFoundException.of("el vendedor", sellerId);
        }
    }
}
