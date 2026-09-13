import { useState } from 'react'
import { orderStatusLabel } from '../api/labels'

/**
 * Transiciones que cada estado admite, en el mismo orden en que las declara el
 * ciclo de vida del pedido.
 *
 * El backend sigue siendo la autoridad: valida la transicion en el enum del
 * dominio y responde 409 si no es admisible, tambien si alguien llama al
 * endpoint sin pasar por esta pantalla. Este mapa no decide nada, solo evita
 * ofrecer opciones que ya se sabe que van a ser rechazadas: pedir el estado que
 * el pedido ya tiene, o uno inalcanzable, es un 409 que el vendedor no tiene
 * forma de anticipar desde la UI. Si el ciclo de vida cambia en el backend y
 * esto queda viejo, el peor caso es un estado que no se ofrece o uno que se
 * ofrece y el servidor rechaza; nunca una transicion invalida aplicada.
 */
const ALLOWED_TRANSITIONS = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
}

/**
 * Estados a los que el pedido puede pasar. Un estado que este frontend no
 * conoce no habilita ninguno: ante la duda no se ofrece una transicion que
 * podria no existir.
 */
export function allowedTransitions(status) {
  return ALLOWED_TRANSITIONS[status] ?? []
}

/**
 * Cambio de estado del pedido, en el detalle de la vista del vendedor.
 *
 * Solo se monta si hay al menos una transicion posible: un pedido en estado
 * terminal (DELIVERED o CANCELLED) no muestra el selector, porque un control
 * que no puede hacer nada es peor que su ausencia.
 */
function OrderStatusForm({ status, onChangeStatus }) {
  const targets = allowedTransitions(status)
  const [target, setTarget] = useState(targets[0])
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    try {
      await onChangeStatus(target)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form status-change" onSubmit={handleSubmit}>
      <label>
        Cambiar estado
        <select
          value={target}
          onChange={(event) => setTarget(event.target.value)}
          disabled={busy}
        >
          {targets.map((candidate) => (
            <option key={candidate} value={candidate}>
              {orderStatusLabel(candidate)}
            </option>
          ))}
        </select>
      </label>

      <div className="form-actions">
        {/* Deshabilitado mientras esta en vuelo: un doble click mandaria la
            misma transicion dos veces y la segunda seria un 409, un error que
            el vendedor no cometio. */}
        <button type="submit" disabled={busy}>
          {busy ? 'Aplicando...' : 'Aplicar'}
        </button>
      </div>
    </form>
  )
}

export default OrderStatusForm
