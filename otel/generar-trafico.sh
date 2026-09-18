#!/usr/bin/env bash
# Genera los escenarios que el anexo pide demostrar: exito, error de negocio
# (409), error de validacion (400) y el disparo de la notificacion asincronica.
#
# No es idempotente: crea preguntas y el store es en memoria. Para repetirlo
# desde un estado conocido, `docker compose restart backend` antes de correrlo.

set -u
API="${API:-http://localhost:8080}"
S1=10000000-0000-0000-0000-000000000001
O1=30000000-0000-0000-0000-000000000001
O6=30000000-0000-0000-0000-000000000006
P3=20000000-0000-0000-0000-000000000003

call() { printf '  %-58s -> HTTP %s\n' "$1" "$(curl -s -o /dev/null -w '%{http_code}' "${@:2}")"; }

echo "ESCENARIOS EXITOSOS"
call "GET  cola de Operaciones"            "$API/api/ops/questions/unresolved"
call "GET  pedidos del vendedor"           "$API/api/sellers/$S1/orders"
call "GET  pedidos filtrados por estado"   "$API/api/sellers/$S1/orders?status=PENDING&status=CONFIRMED"
call "GET  detalle de pedido"              "$API/api/sellers/$S1/orders/$O6"
call "PATCH avanzar pedido a CONFIRMED"    -X PATCH "$API/api/sellers/$S1/orders/$O1/status" \
     -H 'Content-Type: application/json' -d '{"status":"CONFIRMED"}'

echo "NOTIFICACION ASINCRONICA (la pregunta clasifica HIGH y dispara el aviso)"
call "POST pregunta con palabras clave"    -X POST "$API/api/orders/$O6/questions" \
     -H 'Content-Type: application/json' \
     -d '{"productId":"'"$P3"'","questionText":"Necesito la devolucion urgente, esto es un reclamo"}'

echo "ESCENARIOS CON ERROR"
call "PATCH transicion invalida (409)"     -X PATCH "$API/api/sellers/$S1/orders/$O6/status" \
     -H 'Content-Type: application/json' -d '{"status":"SHIPPED"}'
call "GET  rango de fechas invertido (400)" "$API/api/sellers/$S1/orders?from=2026-09-10&to=2026-09-01"
call "GET  estado inexistente (400)"       "$API/api/sellers/$S1/orders?status=EN_ADUANA"
call "GET  pedido inexistente (404)"       "$API/api/sellers/$S1/orders/30000000-0000-0000-0000-0000000000ff"
call "GET  UUID mal formado (400)"         "$API/api/sellers/$S1/orders/no-es-un-uuid"

echo
echo "Trazas   -> http://localhost:16686  (servicio seller-dashboard-api)"
echo "Metricas -> http://localhost:9090"
