import { useState } from 'react'

/** Valor del selector cuando la pregunta no es sobre un item puntual. */
const WHOLE_ORDER = ''

/**
 * Alta de una pregunta, al pie del chat.
 *
 * Rotulado explicitamente como simulacion del comprador: el comprador no es una
 * entidad del modelo y no tiene vista propia, pero sin esta forma de crear
 * preguntas el disparo de notificaciones solo se podria mostrar con curl (ver
 * DECISIONS.md). Queda en la vista del vendedor y no como un tercer rol del
 * selector, porque darle una vista propia sugeriria que el comprador es un
 * actor del sistema.
 */
function NewQuestionForm({ lines, onCreate }) {
  const [questionText, setQuestionText] = useState('')
  const [productId, setProductId] = useState(WHOLE_ORDER)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    try {
      await onCreate(questionText, productId || null)
      // Se limpia siempre, tambien si fallo: el error queda en el banner, y
      // conservar el texto de un intento fallido invita a reenviar lo mismo.
      setQuestionText('')
      setProductId(WHOLE_ORDER)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form new-question" onSubmit={handleSubmit}>
      <h4>Nueva pregunta (simulacion del comprador)</h4>

      <label>
        Producto
        {/* Solo los productos de las lineas de este pedido: preguntar por un
            producto ajeno al pedido es un 400 del backend, asi que no se
            ofrece. */}
        <select value={productId} onChange={(event) => setProductId(event.target.value)}>
          <option value={WHOLE_ORDER}>Sobre el pedido en general</option>
          {lines.map((line) => (
            <option key={line.productId} value={line.productId}>
              {line.productName}
            </option>
          ))}
        </select>
      </label>

      <textarea
        rows={3}
        value={questionText}
        onChange={(event) => setQuestionText(event.target.value)}
        placeholder="Escribi la consulta del comprador"
        disabled={busy}
      />

      <div className="form-actions">
        <button type="submit" disabled={busy || !questionText.trim()}>
          {busy ? 'Enviando...' : 'Enviar pregunta'}
        </button>
      </div>
    </form>
  )
}

export default NewQuestionForm
