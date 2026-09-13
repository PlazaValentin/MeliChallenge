import { useState } from 'react'

/**
 * Vendedores disponibles, con sus ids del seed.
 *
 * Estan en el cliente como consecuencia de no tener autenticacion: en un
 * sistema real el sellerId sale del token de sesion y no de una lista que el
 * frontend conoce. Por eso tampoco se agrego un endpoint de vendedores, que
 * seria resolver por otra via algo que en realidad resuelve el login (ver
 * DECISIONS.md, "Sin login" y "Sin restriccion de acceso entre vendedores").
 */
const SELLERS = [
  { id: '10000000-0000-0000-0000-000000000001', name: 'TecnoHogar' },
  { id: '10000000-0000-0000-0000-000000000002', name: 'Deportes Andes' },
]

/**
 * Raiz de la aplicacion.
 *
 * Concentra el unico estado compartido: que vista se esta mirando, con que
 * vendedor y sobre que pedido. No hay router: son dos vistas con navegacion
 * lineal, y agregar la dependencia solo daria URLs compartibles y boton de
 * atras, que esta demo no necesita.
 *
 * El pedido seleccionado vive aca y no dentro de cada vista porque ambas
 * pueden abrir el detalle, y asi el componente de detalle se reutiliza sin que
 * una vista tenga que conocer el estado de la otra.
 */
function App() {
  const [view, setView] = useState('seller')
  const [sellerId, setSellerId] = useState(SELLERS[0].id)
  const [selectedOrderId, setSelectedOrderId] = useState(null)

  // Cambiar de vista o de vendedor deja sin sentido el pedido abierto: el
  // detalle se resuelve dentro del vendedor de la ruta, y un pedido de otro
  // vendedor responderia 404.
  function selectView(nextView) {
    setView(nextView)
    setSelectedOrderId(null)
  }

  function selectSeller(nextSellerId) {
    setSellerId(nextSellerId)
    setSelectedOrderId(null)
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Gestion de pedidos y preguntas</h1>

        <div className="app-controls">
          <label>
            Vista
            <select value={view} onChange={(event) => selectView(event.target.value)}>
              <option value="seller">Vendedor</option>
              <option value="ops">Operaciones</option>
            </select>
          </label>

          {/* El vendedor solo aplica a su propia vista: la cola de Operaciones
              es global y cruza vendedores por definicion. */}
          {view === 'seller' && (
            <label>
              Vendedor
              <select value={sellerId} onChange={(event) => selectSeller(event.target.value)}>
                {SELLERS.map((seller) => (
                  <option key={seller.id} value={seller.id}>
                    {seller.name}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
      </header>

      <main>
        {/* Las vistas se implementan en los bloques siguientes. */}
        <p className="empty">
          Vista <strong>{view}</strong>
          {view === 'seller' ? ` — vendedor ${sellerId}` : ''}
          {selectedOrderId ? ` — pedido ${selectedOrderId}` : ''}
        </p>
      </main>
    </div>
  )
}

export default App
