import { priorityLabel } from '../api/labels'

// Piezas de presentacion compartidas por las dos vistas. Van juntas en un
// archivo porque cada una es una linea de markup: separarlas daria cuatro
// archivos triviales sin ganar nada en claridad.

/**
 * Error de la API. Muestra la descripcion y, si los hay, los fallos de
 * validacion campo por campo: el backend los devuelve todos juntos para que se
 * puedan corregir en una sola pasada en lugar de descubrirlos de a uno.
 */
export function ErrorBanner({ error }) {
  if (!error) return null

  return (
    <div className="error-banner" role="alert">
      <strong>{error.description}</strong>
      {error.errors?.length > 0 && (
        <ul>
          {error.errors.map((fieldError) => (
            <li key={fieldError.field}>
              {fieldError.field}: {fieldError.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function Loading({ text = 'Cargando...' }) {
  return <p className="loading">{text}</p>
}

/**
 * Vacio explicito. Cada pantalla pasa su propio texto porque los vacios no
 * significan lo mismo: un listado sin resultados es un filtro que no matchea,
 * una cola vacia es una buena noticia y un pedido sin preguntas es lo normal.
 */
export function EmptyState({ text }) {
  return <p className="empty">{text}</p>
}

/**
 * Clasificacion de prioridad del pedido o de la pregunta.
 *
 * Un pedido sin preguntas sin resolver no tiene prioridad, y eso es distinto de
 * tenerla baja: "sin preguntas" y "preguntas triviales" no son lo mismo, y
 * mostrar un badge igual le mentiria al vendedor. Por eso el vacio se muestra
 * como un guion y no como LOW (ver DECISIONS.md).
 */
export function PriorityBadge({ priority }) {
  if (!priority) return <span className="chat-meta">—</span>

  return (
    <span className={`badge badge-${priority.toLowerCase()}`}>{priorityLabel(priority)}</span>
  )
}
