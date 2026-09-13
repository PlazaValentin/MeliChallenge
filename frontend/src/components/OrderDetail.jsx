import { useCallback, useEffect, useState } from 'react'
import {
  answerQuestion,
  createQuestion,
  getOrderDetail,
  resolveQuestion,
} from '../api/client'
import { formatDate, formatMoney, orderStatusLabel } from '../api/labels'
import NewQuestionForm from './NewQuestionForm'
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

  /**
   * Ejecuta una accion del vendedor y vuelve a pedir el detalle al backend, en
   * vez de actualizar el estado local con la respuesta: la prioridad y el flag
   * de preguntas pendientes se derivan al consultar y no se persisten, asi que
   * recalcularlos aca seria duplicar la regla de negocio en la punta
   * equivocada. Por eso las acciones devuelven solo el id.
   */
  const runAction = useCallback(
    async (action) => {
      setError(null)
      try {
        await action()
      } catch (apiError) {
        setError(apiError)
        return
      }
      await load()
    },
    [load],
  )

  const handleAnswer = useCallback(
    (questionId, answerText) => runAction(() => answerQuestion(questionId, answerText)),
    [runAction],
  )

  const handleResolve = useCallback(
    (questionId) => runAction(() => resolveQuestion(questionId)),
    [runAction],
  )

  // Crear tambien recarga: la pregunta nueva cambia la prioridad del pedido y
  // el flag de pendientes, y la respuesta trae solo el id.
  const handleCreate = useCallback(
    (questionText, productId) =>
      runAction(() => createQuestion(orderId, questionText, productId)),
    [runAction, orderId],
  )

  return (
    <section className="panel">
      <button type="button" onClick={onBack}>
        ← Volver
      </button>

      <ErrorBanner error={error} />
      {loading && <Loading />}

      {order && (
        <>
          {/* El UUID completo, no truncado: aca es donde sirve para copiarlo o
              usarlo en un curl, y un prefijo comun lo haria inservible. */}
          <h2>Pedido</h2>
          <p className="order-id">{order.id}</p>

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
          <QuestionChat
            questions={order.questions}
            readOnly={readOnly}
            onAnswer={handleAnswer}
            onResolve={handleResolve}
          />

          {/* Operaciones no simula al comprador: el formulario existe para
              demostrar el disparo de notificaciones desde la vista del
              vendedor, y en modo lectura no se muestra nada que escriba. */}
          {!readOnly && <NewQuestionForm lines={order.lines} onCreate={handleCreate} />}
        </>
      )}
    </section>
  )
}

export default OrderDetail
