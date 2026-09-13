import { formatDateTime, questionStatusLabel } from '../api/labels'
import { EmptyState } from './ui'

/**
 * Conversacion entre el comprador y el vendedor sobre un pedido.
 *
 * No existe una entidad de mensaje: cada pregunta es un turno con su unica
 * respuesta, y la conversacion emerge de la secuencia de preguntas del pedido
 * ordenadas por fecha (ver DECISIONS.md, "Una pregunta admite una sola
 * respuesta"). El orden viene del backend; aca no se ordena.
 *
 * Se comparte entre las dos vistas. La diferencia no esta en el componente sino
 * en el permiso: Operaciones lo ve en modo lectura, porque solo el vendedor
 * responde las dudas del comprador.
 */
function QuestionChat({ questions, readOnly = false }) {
  if (questions.length === 0) {
    // La seccion no se oculta cuando no hay preguntas: un bloque ausente no se
    // distingue de uno que todavia carga o que fallo. Ademas es el caso normal,
    // no una anomalia.
    return <EmptyState text="Este pedido no tiene preguntas." />
  }

  return (
    <div className="chat">
      {questions.map((question) => (
        <article key={question.id} className="chat-turn">
          <div className="chat-message chat-question">{question.questionText}</div>
          <div className="chat-meta">
            Comprador · {formatDateTime(question.createdAt)} ·{' '}
            {questionStatusLabel(question.status)}
          </div>

          {question.answerText && (
            <>
              <div className="chat-message chat-answer">{question.answerText}</div>
              {/* La respuesta no tiene fecha propia: no se persiste un
                  answeredAt, porque el tiempo que se pondera es siempre el de
                  creacion de la pregunta (ver DECISIONS.md). */}
              <div className="chat-meta">Vendedor</div>
            </>
          )}

          {/* Las acciones del vendedor se agregan en el bloque siguiente. En
              modo lectura no se muestran nunca. */}
          {!readOnly && null}
        </article>
      ))}
    </div>
  )
}

export default QuestionChat
