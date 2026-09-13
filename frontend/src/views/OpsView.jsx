import { useCallback, useEffect, useState } from 'react'
import { listUnresolvedQuestions } from '../api/client'
import {
  formatDateTime,
  formatMoney,
  orderStatusLabel,
  questionStatusLabel,
  SCORE_FACTOR_LABELS,
} from '../api/labels'
import { SELLERS, sellerName } from '../api/sellers'
import OrderDetail from '../components/OrderDetail'
import { EmptyState, ErrorBanner, Loading, PriorityBadge } from '../components/ui'

/** Valor del selector cuando no se filtra. La cola es global por defecto. */
const ALL_SELLERS = ''

/**
 * Vista de Operaciones: cruza pedidos en vez de bajar a uno.
 *
 * Lista todas las preguntas sin resolver (OPEN y ANSWERED) de todos los
 * vendedores, en el orden que devuelve el backend. Aca no se ordena: el criterio
 * (score descendente y, ante empate, la mas antigua primero) es una regla de
 * negocio y vive donde se calcula el score.
 */
function OpsView() {
  const [questions, setQuestions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [sellerId, setSellerId] = useState(ALL_SELLERS)
  // El par (vendedor, pedido) sale de la fila y no del header, asi que vive
  // aca y no en la raiz.
  const [selected, setSelected] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await listUnresolvedQuestions(sellerId || undefined)
      setQuestions(response.items)
    } catch (apiError) {
      setError(apiError)
    } finally {
      setLoading(false)
    }
  }, [sellerId])

  useEffect(() => {
    load()
  }, [load])

  if (selected) {
    return (
      <OrderDetail
        sellerId={selected.sellerId}
        orderId={selected.orderId}
        // Operaciones no responde ni resuelve: solo el vendedor contesta las
        // dudas del comprador. El componente es el mismo; la diferencia esta en
        // el permiso.
        readOnly
        onBack={() => {
          setSelected(null)
          // La cola puede haber cambiado mientras se miraba el detalle, y ahi no
          // hay accion posible que la invalide, pero si hay otro operador
          // trabajando sobre la misma cola.
          load()
        }}
      />
    )
  }

  return (
    <section className="panel">
      <h2>Preguntas sin resolver</h2>

      {/* El filtro por vendedor es de esta cola y por eso vive con ella, no en
          el header: alli significaria otra cosa segun la vista. */}
      <div className="filters">
        <label>
          Vendedor
          <select value={sellerId} onChange={(event) => setSellerId(event.target.value)}>
            <option value={ALL_SELLERS}>Todos</option>
            {SELLERS.map((seller) => (
              <option key={seller.id} value={seller.id}>
                {seller.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <ErrorBanner error={error} />
      {loading && <Loading />}

      {!loading && !error && questions.length === 0 && (
        // Una cola vacia no es un filtro que no matchea: es que no hay trabajo
        // pendiente, que es una buena noticia y se dice como tal.
        <EmptyState text="No hay preguntas sin resolver." />
      )}

      {questions.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Pregunta</th>
              <th>Pedido</th>
              <th className="numeric">Score</th>
              <th>Prioridad</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {questions.map((question) => (
              <QuestionRow
                key={question.id}
                question={question}
                onOpenOrder={() =>
                  setSelected({ sellerId: question.sellerId, orderId: question.orderId })
                }
              />
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

/**
 * Fila de la cola, con su desglose colapsable.
 *
 * El desglose arranca cerrado y cada fila maneja su propio estado: la cola se
 * lee de un vistazo por score, y los cinco factores quedan a un click para
 * cuando haga falta entender por que una pregunta quedo arriba de otra.
 */
function QuestionRow({ question, onOpenOrder }) {
  const [showBreakdown, setShowBreakdown] = useState(false)

  return (
    <>
      <tr>
        <td>
          <div>{question.questionText}</div>
          <div className="chat-meta">
            {formatDateTime(question.createdAt)} · {questionStatusLabel(question.status)}
          </div>
        </td>
        <td>
          {/* Sin el id del pedido: con el prefijo comun del seed todas las filas
              se verian iguales. El estado y el monto son dos de los factores del
              score, y tenerlos aca evita abrir el detalle solo para entender el
              puntaje. */}
          <div>{sellerName(question.sellerId)}</div>
          <div className="chat-meta">
            {orderStatusLabel(question.orderStatus)} · {formatMoney(question.orderTotalAmount)}
          </div>
        </td>
        <td className="numeric">{question.score.total}</td>
        <td>
          <PriorityBadge priority={question.score.priority} />
        </td>
        <td>
          <div className="row-actions">
            <button type="button" onClick={() => setShowBreakdown((shown) => !shown)}>
              {showBreakdown ? 'Ocultar desglose' : 'Ver desglose'}
            </button>
            <button type="button" onClick={onOpenOrder}>
              Ver pedido
            </button>
          </div>
        </td>
      </tr>

      {showBreakdown && (
        <tr>
          {/* Ocupa el ancho completo y no una columna propia: son cinco valores
              que solo se miran de a una fila por vez, y como columnas fijas
              volverian ilegible la tabla. */}
          <td colSpan={5}>
            <dl className="breakdown">
              {Object.entries(SCORE_FACTOR_LABELS).map(([factor, label]) => (
                <div key={factor}>
                  <dt>{label}</dt>
                  <dd>{question.score.breakdown[factor]}</dd>
                </div>
              ))}
              <div>
                <dt>Total</dt>
                <dd>{question.score.total}</dd>
              </div>
            </dl>
          </td>
        </tr>
      )}
    </>
  )
}

export default OpsView
