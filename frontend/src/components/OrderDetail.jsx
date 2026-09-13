import { useCallback, useEffect, useState } from 'react'
import { getOrderDetail } from '../api/client'
import { formatDate, formatMoney, orderStatusLabel, shortId } from '../api/labels'
import QuestionChat from './QuestionChat'
import { ErrorBanner, Loading, PriorityBadge } from './ui'

/**
 * Detalle de un pedido con sus lineas y su conversacion.
 *
 * Compartido por las dos vistas: el vendedor lo abre desde su listado y
 * Operaciones desde una fila de la cola. La diferencia es readOnly, que
 * determina si el chat permite responder.
 *
 * Hace su propia consulta en vez de recibir el pedido ya cargado: el backend
 * devuelve lineas y preguntas juntas en un solo llamado, y que el componente se
 * traiga lo que necesita evita que cada vista tenga que saber como se arma el
 * detalle.
 */
function OrderDetail({ sellerId, orderId, readOnly = false, onBack }) {
  const [order, setOrder] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    // El banner se limpia antes de cada intento: si no, el error viejo queda en
    // pantalla mientras la nueva request esta en vuelo.
    setError(null)
    try {
      setOrder(await getOrderDetail(sellerId, orderId))
    } catch (apiError) {
      setError(apiError)
    } finally {
      setLoading(false)
    }
  }, [sellerId, orderId])

  useEffect(() => {
    load()
  }, [load])

  return (
    <section className="panel">
      <button type="button" onClick={onBack}>
        ← Volver
      </button>

      <ErrorBanner error={error} />
      {loading && <Loading />}

      {order && (
        <>
          <h2>Pedido {shortId(order.id)}</h2>

          <p className="chat-meta">
            {order.buyer.name} ({order.buyer.email}) · {formatDate(order.createdAt)} ·{' '}
            {orderStatusLabel(order.status)}
          </p>

          <h3>Items</h3>
          <table>
            <thead>
              <tr>
                <th>Producto</th>
                <th className="numeric">Cantidad</th>
                <th className="numeric">Precio unitario</th>
                <th className="numeric">Subtotal</th>
              </tr>
            </thead>
            <tbody>
              {order.lines.map((line) => (
                <tr key={line.productId}>
                  <td>{line.productName}</td>
                  <td className="numeric">{line.quantity}</td>
                  <td className="numeric">{formatMoney(line.unitPrice)}</td>
                  {/* El subtotal lo calcula el backend: repartir logica de
                      negocio entre las dos puntas complica el diagnostico
                      cuando el numero no cierra. */}
                  <td className="numeric">{formatMoney(line.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th colSpan={3}>Total</th>
                <td className="numeric">
                  <strong>{formatMoney(order.totalAmount)}</strong>
                </td>
              </tr>
            </tfoot>
          </table>

          <h3>
            Conversacion <PriorityBadge priority={order.priority} />
          </h3>
          <QuestionChat questions={order.questions} readOnly={readOnly} />
        </>
      )}
    </section>
  )
}

export default OrderDetail
