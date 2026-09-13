import { useCallback, useEffect, useState } from 'react'
import { listOrders } from '../api/client'
import { formatDate, formatMoney, orderStatusLabel, shortId } from '../api/labels'
import OrderDetail from '../components/OrderDetail'
import { EmptyState, ErrorBanner, Loading, PriorityBadge } from '../components/ui'

/**
 * Vista del vendedor: se centra en un pedido y baja al detalle.
 *
 * La tabla no esta en un componente aparte porque no se usa fuera de esta
 * vista; extraerla daria un archivo mas sin ningun reuso.
 */
function SellerView({ sellerId, selectedOrderId, onSelectOrder }) {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // Los listados vienen envueltos en un objeto con `items`, para que
      // agregar paginacion no rompa el contrato.
      const response = await listOrders(sellerId)
      setOrders(response.items)
    } catch (apiError) {
      setError(apiError)
    } finally {
      setLoading(false)
    }
  }, [sellerId])

  useEffect(() => {
    load()
  }, [load])

  if (selectedOrderId) {
    return (
      <OrderDetail
        sellerId={sellerId}
        orderId={selectedOrderId}
        // Al volver se recarga el listado: la prioridad y el flag de preguntas
        // pendientes se derivan al consultar, asi que las acciones hechas en el
        // detalle los cambiaron y lo que hay en memoria quedo viejo. Se recarga
        // siempre y no solo cuando hubo una accion, para no tener que llevar la
        // cuenta de si el detalle modifico algo.
        onBack={() => {
          onSelectOrder(null)
          load()
        }}
      />
    )
  }

  return (
    <section className="panel">
      <h2>Pedidos</h2>

      <ErrorBanner error={error} />
      {loading && <Loading />}

      {!loading && !error && orders.length === 0 && (
        <EmptyState text="No hay pedidos para este vendedor." />
      )}

      {orders.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Pedido</th>
              <th>Fecha</th>
              <th>Comprador</th>
              <th>Estado</th>
              <th className="numeric">Total</th>
              <th>Preguntas</th>
              <th>Prioridad</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td>{shortId(order.id)}</td>
                <td>{formatDate(order.createdAt)}</td>
                <td>{order.buyer.name}</td>
                <td>{orderStatusLabel(order.status)}</td>
                <td className="numeric">{formatMoney(order.totalAmount)}</td>
                {/* No es un contador sino un "respondiste / no respondiste":
                    le dice al vendedor que tiene algo que contestar, y no
                    cuantas cosas (ver DECISIONS.md). */}
                <td>{order.hasPendingQuestions ? 'Sin responder' : '—'}</td>
                <td>
                  <PriorityBadge priority={order.priority} />
                </td>
                <td>
                  <button type="button" onClick={() => onSelectOrder(order.id)}>
                    Ver detalle
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

export default SellerView
