import { ORDER_STATUS_LABELS } from '../api/labels'

/**
 * Filtros del listado de pedidos: estado, rango de fechas y comprador.
 *
 * No hay boton de aplicar. Estado y fechas son eventos discretos y se aplican al
 * cambiar; el texto del comprador lo aplica la vista con un retardo, porque
 * disparar una request por tecla ademas de ser innecesario abre la puerta a que
 * las respuestas lleguen desordenadas.
 *
 * Es un componente aparte de la tabla porque son dos cosas distintas: una toma
 * la entrada del usuario y la otra muestra el resultado. Juntas darian un
 * archivo donde el markup del formulario tapa el del listado.
 */
function OrderFilters({ statuses, from, to, buyer, hasFilters, onChange, onClear }) {
  // Ningun estado tildado significa "todos": es la ausencia del filtro, no un
  // filtro que no matchea nada.
  function toggleStatus(status) {
    onChange({
      statuses: statuses.includes(status)
        ? statuses.filter((value) => value !== status)
        : [...statuses, status],
    })
  }

  return (
    <div className="filters">
      <fieldset className="filter-statuses">
        <legend>Estado</legend>
        {/* Checkboxes y no un select multiple: la multiseleccion nativa exige
            ctrl+click y no se descubre sola. Son cinco valores fijos y entran
            en una fila. */}
        {Object.entries(ORDER_STATUS_LABELS).map(([status, label]) => (
          <label key={status} className="filter-check">
            <input
              type="checkbox"
              checked={statuses.includes(status)}
              onChange={() => toggleStatus(status)}
            />
            {label}
          </label>
        ))}
      </fieldset>

      <label>
        Desde
        {/* El valor del input ya es yyyy-MM-dd, que es lo que espera el backend:
            convertirlo a Date en el medio solo agregaria un corrimiento de zona
            horaria al valor que el usuario eligio. */}
        <input
          type="date"
          value={from}
          onChange={(event) => onChange({ from: event.target.value })}
        />
      </label>

      <label>
        Hasta
        <input type="date" value={to} onChange={(event) => onChange({ to: event.target.value })} />
      </label>

      <label>
        Comprador
        <input
          type="search"
          value={buyer}
          placeholder="Nombre o email"
          onChange={(event) => onChange({ buyer: event.target.value })}
        />
      </label>

      <button type="button" onClick={onClear} disabled={!hasFilters}>
        Limpiar filtros
      </button>
    </div>
  )
}

export default OrderFilters
