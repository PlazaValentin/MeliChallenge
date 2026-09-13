import { useState } from 'react'
import { SELLERS } from './api/sellers'
import OpsView from './views/OpsView'
import SellerView from './views/SellerView'

/**
 * Raiz de la aplicacion.
 *
 * Concentra el estado compartido: que vista se esta mirando, con que vendedor y
 * sobre que pedido. No hay router: son dos vistas con navegacion lineal, y
 * agregar la dependencia solo daria URLs compartibles y boton de atras, que
 * esta demo no necesita.
 *
 * El pedido abierto por Operaciones no vive aca sino en su vista: no es el
 * mismo dato. En la vista del vendedor el vendedor viene del header y solo se
 * elige el pedido; en Operaciones el par (vendedor, pedido) sale de la fila, y
 * compartir el estado haria que el vendedor del header entre en conflicto con
 * el de la fila.
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
        {view === 'seller' ? (
          <SellerView
            // Remonta la vista al cambiar de vendedor, para que no quede
            // mostrando los pedidos del anterior mientras llegan los nuevos.
            key={sellerId}
            sellerId={sellerId}
            selectedOrderId={selectedOrderId}
            onSelectOrder={setSelectedOrderId}
          />
        ) : (
          <OpsView />
        )}
      </main>
    </div>
  )
}

export default App
