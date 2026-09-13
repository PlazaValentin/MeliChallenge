/**
 * Vendedores disponibles, con sus ids del seed.
 *
 * Estan hardcodeados como consecuencia directa de no tener autenticacion: en un
 * sistema real el sellerId sale del token de sesion, no de una lista que el
 * frontend conoce. Por eso tampoco se agrego un endpoint de vendedores, que
 * seria resolver por otra via algo que en realidad resuelve el login (ver
 * DECISIONS.md, "Sin login" y "Sin restriccion de acceso entre vendedores").
 *
 * Vive en su propio modulo y no en la raiz porque es un dato, del mismo tipo
 * que las etiquetas: dejarlo en App obligaria a las vistas a importar de su
 * propio padre.
 */
export const SELLERS = [
  { id: '10000000-0000-0000-0000-000000000001', name: 'TecnoHogar' },
  { id: '10000000-0000-0000-0000-000000000002', name: 'Deportes Andes' },
]

/**
 * Nombre del vendedor para mostrar. Un id que el frontend no conoce se muestra
 * truncado en lugar de vacio, con el mismo criterio que los enums: un vendedor
 * nuevo en el backend tiene que notarse en pantalla, no desaparecer.
 */
export function sellerName(sellerId) {
  return SELLERS.find((seller) => seller.id === sellerId)?.name ?? sellerId.slice(0, 8)
}
