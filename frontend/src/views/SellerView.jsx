import { useCallback, useEffect, useState } from 'react'
import { listOrders } from '../api/client'
import { formatDate, formatMoney, orderStatusLabel } from '../api/labels'
import OrderDetail from '../components/OrderDetail'
import OrderFilters from '../components/OrderFilters'
import { EmptyState, ErrorBanner, Loading, PriorityBadge } from '../components/ui'

/** Sin filtros: el listado trae todos los pedidos del vendedor. */
const NO_FILTERS = { statuses: [], from: '', to: '', buyer: '' }

/**
 * Retardo del filtro de comprador. Sin el, cada tecla dispara una request y las
 * respuestas pueden llegar desordenadas, dejando en pantalla el resultado de
 * una busqueda vieja sobre lo que se escribio despues.
 */
const BUYER_DEBOUNCE_MS = 300

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
  const [filters, setFilters] = useState(NO_FILTERS)
  // El texto que se esta tipeando y el que ya se busco son dos cosas distintas:
  // el input muestra el primero y la consulta usa el segundo.
  const [appliedBuyer, setAppliedBuyer] = useState('')

  const { statuses, from, to, buyer } = filters
  const hasFilters = statuses.length > 0 || Boolean(from) || Boolean(to) || Boolean(buyer)

  useEffect(() => {
    const timer = setTimeout(() => setAppliedBuyer(buyer), BUYER_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [buyer])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // Los filtros vacios no viajan: el cliente los descarta al armar el query
      // string, porque un parametro presente y vacio no es lo mismo que ausente.
      // Los tres combinan con AND.
      const response = await listOrders(sellerId, { statuses, from, to, buyer: appliedBuyer })
      setOrders(response.items)
    } catch (apiError) {
      // Un rango invertido es un 400 del backend y se muestra en el banner. No
      // se previene aca: la validacion vive en un solo lado, y adelantarla
      // significaria mantener la misma regla en las dos puntas.
      setError(apiError)
      setOrders([])
    } finally {
      setLoading(false)
    }
  }, [sellerId, statuses, from, to, appliedBuyer])

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

      <OrderFilters
        statuses={statuses}
        from={from}
        to={to}
        buyer={buyer}
        hasFilters={hasFilters}
        onChange={(change) => setFilters((current) => ({ ...current, ...change }))}
        onClear={() => setFilters(NO_FILTERS)}
      />

      <ErrorBanner error={error} />
      {loading && <Loading />}

      {!loading && !error && orders.length === 0 && (
        // Un filtro valido sin resultados no es un error: el backend responde
        // 200 con lista vacia, y el texto lo dice segun haya filtros o no.
        <EmptyState
          text={
            hasFilters
              ? 'Ningun pedido coincide con los filtros.'
              : 'No hay pedidos para este vendedor.'
          }
        />
      )}

      {orders.length > 0 && (
        <table>
          <thead>
            <tr>
              {/* Sin columna de id: el pedido se identifica por comprador y
                  fecha. El UUID vive en el detalle, donde sirve para copiarlo. */}
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
