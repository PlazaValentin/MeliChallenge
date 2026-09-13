import { useState } from 'react'
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
function QuestionChat({ questions, readOnly = false, onAnswer, onResolve }) {
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
          {/* Solo cuando la pregunta es sobre un item puntual: sin productId es
              una consulta sobre el pedido en general, y una etiqueta generica
              no agregaria nada. El nombre viene resuelto del backend. */}
          {question.productName && (
            <div className="chat-product">Sobre: {question.productName}</div>
          )}
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

          {/* En modo lectura no se muestra ninguna accion: solo el vendedor
              responde las dudas del comprador. */}
          {!readOnly && (
            <QuestionActions
              question={question}
              onAnswer={onAnswer}
              onResolve={onResolve}
            />
          )}
        </article>
      ))}
    </div>
  )
}

/**
 * Acciones disponibles sobre una pregunta, segun su estado.
 *
 * Cada turno maneja su propio texto y su propio "en vuelo" en vez de que el
 * chat lleve un mapa por pregunta: el dato no se usa fuera del turno al que
 * pertenece.
 *
 * Responder y resolver son acciones distintas y estrictamente secuenciales
 * (OPEN -> ANSWERED -> RESOLVED), asi que nunca se ofrecen las dos a la vez.
 * El boton se deshabilita mientras la accion esta en vuelo para evitar el doble
 * click, que en responder seria un 409 (una pregunta admite una sola
 * respuesta).
 */
function QuestionActions({ question, onAnswer, onResolve }) {
  const [answerText, setAnswerText] = useState('')
  const [busy, setBusy] = useState(false)

  async function run(action) {
    setBusy(true)
    try {
      await action()
    } finally {
      // El error lo muestra el banner del detalle; aca solo se libera el boton
      // para que se pueda reintentar.
      setBusy(false)
    }
  }

  if (question.status === 'OPEN') {
    return (
      <form
        className="form chat-actions"
        onSubmit={(event) => {
          event.preventDefault()
          run(() => onAnswer(question.id, answerText))
        }}
      >
        <textarea
          rows={3}
          value={answerText}
          onChange={(event) => setAnswerText(event.target.value)}
          placeholder="Escribi tu respuesta"
          disabled={busy}
        />
        <div className="form-actions">
          {/* El texto obligatorio tambien lo valida el backend (400): esto solo
              evita el viaje para un caso que ya se sabe invalido. */}
          <button type="submit" disabled={busy || !answerText.trim()}>
            {busy ? 'Enviando...' : 'Responder'}
          </button>
        </div>
      </form>
    )
  }

  if (question.status === 'ANSWERED') {
    return (
      <div className="chat-actions">
        <button type="button" disabled={busy} onClick={() => run(() => onResolve(question.id))}>
          {busy ? 'Resolviendo...' : 'Marcar como resuelta'}
        </button>
      </div>
    )
  }

  return null
}

export default QuestionChat
