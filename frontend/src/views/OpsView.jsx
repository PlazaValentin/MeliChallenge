import { useCallback, useEffect, useState } from 'react'
import { listUnresolvedQuestions } from '../api/client'
import {
  formatDateTime,
  formatMoney,
  formatTime,
  orderStatusLabel,
  questionStatusLabel,
  SCORE_FACTOR_LABELS,
} from '../api/labels'
import { SELLERS, sellerName } from '../api/sellers'
import OrderDetail from '../components/OrderDetail'
import { EmptyState, ErrorBanner, Loading, PriorityBadge } from '../components/ui'

/** Valor del selector cuando no se filtra. La cola es global por defecto. */
const ALL_SELLERS = ''

/** Cada cuanto se refresca la cola por su cuenta, sin que nadie lo pida. */
const POLL_INTERVAL_MS = 30_000

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
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null)
  // El par (vendedor, pedido) sale de la fila y no del header, asi que vive
  // aca y no en la raiz.
  const [selected, setSelected] = useState(null)

  /**
   * Un refresco silencioso no toca el estado de carga: nadie lo pidio, y
   * parpadear el indicador cada 30 segundos sobre datos que siguen en pantalla
   * sugeriria una actividad que el operador no disparo. Una carga a pedido (la
   * primera, el cambio de filtro, la vuelta del detalle) si lo muestra.
   */
  const load = useCallback(
    async ({ silent = false } = {}) => {
      if (!silent) {
        setLoading(true)
        setError(null)
      }
      try {
        const response = await listUnresolvedQuestions(sellerId || undefined)
        setQuestions(response.items)
        setError(null)
        setLastUpdatedAt(new Date())
      } catch (apiError) {
        // La lista no se vacia: lo que ya estaba en pantalla se consulto bien y
        // sigue siendo valido. Solo deja de ser el ultimo estado conocido, y eso
        // lo dice el banner junto a la marca de actualizacion.
        setError(apiError)
      } finally {
        if (!silent) {
          setLoading(false)
        }
      }
    },
    [sellerId],
  )

  useEffect(() => {
    load()
  }, [load])

  // El intervalo se corta al desmontar y tambien mientras el detalle esta
  // abierto: ahi la cola no se ve, y al volver se recarga igual.
  useEffect(() => {
    if (selected) return undefined

    const timer = setInterval(() => load({ silent: true }), POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [load, selected])

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

        {/* Hora absoluta y no "hace X": el relativo obliga a un segundo timer
            solo para que el texto no mienta entre refresco y refresco, y aca lo
            que importa es si el dato es de recien o quedo viejo por un fallo. */}
        {lastUpdatedAt && (
          <p className="last-updated">Actualizado a las {formatTime(lastUpdatedAt)}</p>
        )}
      </div>

      <ErrorBanner error={error} />
      {loading && <Loading />}

      {!loading && !error && questions.length === 0 && (
        // Una cola vacia no es un filtro que no matchea: es que no hay trabajo
        // pendiente, que es una buena noticia y se dice como tal.
        <EmptyState text="No hay preguntas sin resolver." />
      )}

      {/* Durante una carga a pedido la tabla se oculta: dejar las filas del
          vendedor anterior junto al "Cargando..." no permite distinguir si el
          filtro no matcheo o todavia esta en vuelo. El refresco silencioso no
          pasa por aca, asi que la cola nunca parpadea sola. */}
      {!loading && questions.length > 0 && (
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
