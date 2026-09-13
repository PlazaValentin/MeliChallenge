package com.hackerrank.challenge.domain.entity;

import com.hackerrank.challenge.domain.enums.QuestionStatus;
import com.hackerrank.challenge.domain.exception.BusinessRuleException;
import com.hackerrank.challenge.domain.exception.DomainValidationException;
import com.hackerrank.challenge.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionTest {

  private final Order order = TestData.anOrder().build();

  @Nested
  @DisplayName("Creacion")
  class Creation {

    @Test
    @DisplayName("una pregunta nace abierta y sin respuesta")
    void unaPreguntaNaceAbiertaYSinRespuesta() {
      Question question = Question.on(order, null, "Consulta.", TestData.NOW);

      assertThat(question.getStatus()).isEqualTo(QuestionStatus.OPEN);
      assertThat(question.getAnswerText()).isEmpty();
      assertThat(question.getOrderId()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("una pregunta sin producto es sobre el pedido en general")
    void unaPreguntaSinProductoEsSobreElPedidoEnGeneral() {
      assertThat(Question.on(order, null, "Consulta.", TestData.NOW).getProductId()).isEmpty();
    }

    @Test
    @DisplayName("una pregunta puede referirse a un producto del pedido")
    void unaPreguntaPuedeReferirseAUnProductoDelPedido() {
      OrderLine line = TestData.line("1000.00", 1);
      Order orderWithLine = TestData.anOrder().withLines(List.of(line)).build();

      Question question = Question.on(orderWithLine, line.getProductId(), "Consulta.", TestData.NOW);

      assertThat(question.getProductId()).contains(line.getProductId());
    }

    /**
     * Es una regla de negocio y no solo de formato: el producto referenciado
     * tiene que pertenecer al pedido sobre el que se pregunta.
     */
    @Test
    @DisplayName("una pregunta no puede referirse a un producto ajeno al pedido")
    void unaPreguntaNoPuedeReferirseAUnProductoAjenoAlPedido() {
      assertThatThrownBy(() -> Question.on(order, UUID.randomUUID(), "Consulta.", TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rechazaCrearseSinPedido() {
      assertThatThrownBy(() -> Question.on(null, null, "Consulta.", TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rechazaCrearseSinFecha() {
      assertThatThrownBy(() -> Question.on(order, null, "Consulta.", null))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("una pregunta sin texto no existe")
    void unaPreguntaSinTextoNoExiste() {
      assertThatThrownBy(() -> Question.on(order, null, null, TestData.NOW))
          .isInstanceOf(DomainValidationException.class);

      assertThatThrownBy(() -> Question.on(order, null, "   ", TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("el texto de la pregunta se guarda sin espacios sobrantes")
    void elTextoDeLaPreguntaSeGuardaSinEspaciosSobrantes() {
      Question question = Question.on(order, null, "  Consulta.  ", TestData.NOW);

      assertThat(question.getQuestionText()).isEqualTo("Consulta.");
    }

    @Test
    @DisplayName("dos preguntas creadas por separado tienen identidades distintas")
    void dosPreguntasCreadasPorSeparadoTienenIdentidadesDistintas() {
      Question first = Question.on(order, null, "Consulta.", TestData.NOW);
      Question second = Question.on(order, null, "Consulta.", TestData.NOW);

      assertThat(first.getId()).isNotEqualTo(second.getId());
    }
  }

  @Nested
  @DisplayName("Reconstitucion")
  class Reconstitution {

    @Test
    @DisplayName("reconstituir preserva el id original")
    void reconstituirPreservaElIdOriginal() {
      UUID id = UUID.randomUUID();

      Question question = Question.reconstitute(id, order, null, "Consulta.", TestData.NOW);

      assertThat(question.getId()).isEqualTo(id);
    }

    @Test
    void rechazaReconstituirseSinId() {
      assertThatThrownBy(() -> Question.reconstitute(null, order, null, "Consulta.", TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }

    /**
     * Un dataset precargado no puede contener algo que la API no dejaria crear.
     */
    @Test
    @DisplayName("reconstituir aplica las mismas validaciones que crear")
    void reconstituirAplicaLasMismasValidacionesQueCrear() {
      assertThatThrownBy(
          () -> Question.reconstitute(UUID.randomUUID(), order, UUID.randomUUID(), "Consulta.",
              TestData.NOW))
          .isInstanceOf(DomainValidationException.class);

      assertThatThrownBy(
          () -> Question.reconstitute(UUID.randomUUID(), order, null, "  ", TestData.NOW))
          .isInstanceOf(DomainValidationException.class);
    }
  }

  @Nested
  @DisplayName("Respuesta")
  class Answering {

    @Test
    @DisplayName("responder registra el texto y pasa la pregunta a respondida")
    void responderRegistraElTextoYPasaLaPreguntaARespondida() {
      Question question = TestData.aQuestion(order).build();

      question.answer("Ya lo despachamos.");

      assertThat(question.getStatus()).isEqualTo(QuestionStatus.ANSWERED);
      assertThat(question.getAnswerText()).contains("Ya lo despachamos.");
    }

    @Test
    @DisplayName("el texto de la respuesta se guarda sin espacios sobrantes")
    void elTextoDeLaRespuestaSeGuardaSinEspaciosSobrantes() {
      Question question = TestData.aQuestion(order).build();

      question.answer("  Ya lo despachamos.  ");

      assertThat(question.getAnswerText()).contains("Ya lo despachamos.");
    }

    /**
     * Una pregunta admite una sola respuesta: para repreguntar se crea otra
     * pregunta sobre el mismo pedido (ver DECISIONS.md).
     */
    @Test
    @DisplayName("una pregunta ya respondida no admite otra respuesta")
    void unaPreguntaYaRespondidaNoAdmiteOtraRespuesta() {
      Question question = TestData.aQuestion(order).build();
      question.answer("Primera respuesta.");

      assertThatThrownBy(() -> question.answer("Segunda respuesta."))
          .isInstanceOf(BusinessRuleException.class);

      assertThat(question.getAnswerText()).contains("Primera respuesta.");
    }

    @Test
    @DisplayName("una respuesta vacia no se registra")
    void unaRespuestaVaciaNoSeRegistra() {
      Question question = TestData.aQuestion(order).build();

      assertThatThrownBy(() -> question.answer("   "))
          .isInstanceOf(DomainValidationException.class);
      assertThatThrownBy(() -> question.answer(null))
          .isInstanceOf(DomainValidationException.class);

      assertThat(question.getStatus()).isEqualTo(QuestionStatus.OPEN);
    }

    @Test
    @DisplayName("una respuesta invalida no cambia el estado de la pregunta")
    void unaRespuestaInvalidaNoCambiaElEstadoDeLaPregunta() {
      Question question = TestData.aQuestion(order).resolved().build();

      assertThatThrownBy(() -> question.answer("Otra respuesta."))
          .isInstanceOf(BusinessRuleException.class);

      assertThat(question.getStatus()).isEqualTo(QuestionStatus.RESOLVED);
    }
  }

  @Nested
  @DisplayName("Resolucion")
  class Resolution {

    @Test
    @DisplayName("resolver una pregunta respondida la cierra")
    void resolverUnaPreguntaRespondidaLaCierra() {
      Question question = TestData.aQuestion(order).answered().build();

      question.resolve();

      assertThat(question.getStatus()).isEqualTo(QuestionStatus.RESOLVED);
      assertThat(question.isUnresolved()).isFalse();
    }

    @Test
    @DisplayName("una pregunta abierta no puede resolverse sin ser respondida")
    void unaPreguntaAbiertaNoPuedeResolverseSinSerRespondida() {
      Question question = TestData.aQuestion(order).build();

      assertThatThrownBy(question::resolve).isInstanceOf(BusinessRuleException.class);

      assertThat(question.getStatus()).isEqualTo(QuestionStatus.OPEN);
    }

    @Test
    @DisplayName("una pregunta ya resuelta no vuelve a resolverse")
    void unaPreguntaYaResueltaNoVuelveAResolverse() {
      Question question = TestData.aQuestion(order).resolved().build();

      assertThatThrownBy(question::resolve).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("sin resolver abarca las abiertas y las respondidas")
    void sinResolverAbarcaLasAbiertasYLasRespondidas() {
      assertThat(TestData.aQuestion(order).build().isUnresolved()).isTrue();
      assertThat(TestData.aQuestion(order).answered().build().isUnresolved()).isTrue();
      assertThat(TestData.aQuestion(order).resolved().build().isUnresolved()).isFalse();
    }
  }
}
