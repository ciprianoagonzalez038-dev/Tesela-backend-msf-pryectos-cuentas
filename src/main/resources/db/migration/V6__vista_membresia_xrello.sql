-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Base de datos: db_xrello (compartida con ms-xrello-tableros — ver
--                XRELLO_CONTRATO.md §16/§17)
-- Version:       V6 — vw_xrello_membresia: CONTRATO PÚBLICO de solo lectura
--                hacia ms-xrello-tableros.
--
-- ============================================================================
-- POR QUÉ ESTA VISTA EXISTE (ver ANALISIS_AUTORIZACION_TABLEROS.md, ahora en
-- MS/documentos/ANALISIS_AUTORIZACION_TABLEROS.md, y la decisión de
-- arquitectura que lo sucede): ms-xrello-tableros necesita saber, en CADA
-- petición autorizada, si un usuario es miembro de un proyecto y con qué rol
-- — dato que vive exclusivamente en las tablas cta_* de este servicio. Las
-- opciones evaluadas (llamada remota con caché, JWT con membresías, réplica
-- por eventos) todas pagaban latencia de red en el camino más caliente del
-- producto (mover una tarjeta) o retrasaban la propagación de un cambio de
-- rol. Mapear las tablas cta_* directamente desde tableros resolvía eso, pero
-- acoplaba tableros al esquema INTERNO de cuentas (cualquier renombre de
-- columna en cta_miembro_proyecto rompería tableros en silencio).
--
-- Esta vista es el punto medio: una consulta LOCAL (sin red, sin TTL que
-- esperar, cambios de rol visibles en la siguiente transacción) que publica
-- SOLO las columnas que tableros necesita, bajo un nombre y una forma que
-- cuentas se compromete a mantener estables.
--
-- ============================================================================
-- CONTRATO — REGLAS PARA QUIEN TOQUE ESTE SERVICIO EN EL FUTURO:
--
--   1. Las tablas cta_miembro_proyecto, cta_proyecto y cta_rol_proyecto de
--      abajo SIGUEN SIENDO PRIVADAS de ms-xrello-cuentas — se pueden
--      renombrar, dividir, o reestructurar libremente, SIEMPRE que esta vista
--      seguida produzca exactamente las mismas columnas, con el mismo nombre
--      y el mismo tipo, para las mismas filas lógicas.
--   2. Cambiar el NOMBRE de una columna de esta vista, QUITAR una columna, o
--      cambiar el tipo de datos de una columna existente es un CAMBIO QUE
--      ROMPE ms-xrello-tableros (su entidad JPA de solo lectura,
--      membresia.MembresiaVista, está mapeada 1:1 contra esta forma) — exige
--      una migración COORDINADA entre ambos servicios, nunca un cambio
--      unilateral en uno solo.
--   3. AGREGAR una columna nueva al final (ej. si tableros necesita un dato
--      adicional en el futuro) es seguro de forma unilateral — un consumidor
--      JPA que no mapea esa columna simplemente la ignora.
--   4. Esta migración NO se edita retroactivamente una vez aplicada (mismo
--      criterio que toda migración Flyway ya aplicada, ver XRELLO_CONTRATO.md
--      §16) — un cambio de forma futuro es una migración NUEVA (V7, V8...)
--      que vuelve a hacer CREATE OR REPLACE VIEW con la forma nueva.
--
-- ============================================================================
-- ÍNDICE PARA LA CONSULTA (usuario_id, proyecto_id): ya existe
-- ix_cta_miembro_proyecto_usuario (usuario_id) desde V1 — explícito
-- precisamente para esta clase de consulta, ver el comentario de V1 sección 9
-- ("la consulta del Home... filtra por usuario_id solo, y el UNIQUE
-- (proyecto_id, usuario_id) no sirve para ese acceso porque usuario_id no es
-- su columna líder"). La vista de abajo se consulta por
-- (proyecto_id, usuario_id) — cubierta por el UNIQUE
-- uq_cta_miembro_proyecto_proyecto_usuario (proyecto_id, usuario_id), cuya
-- columna líder SÍ es proyecto_id — y por proyecto_codigo sola (vía
-- p.codigo, ya UNIQUE en cta_proyecto) — ambos accesos ya tienen índice
-- utilizable, no hace falta ninguno nuevo.
-- ============================================================================
CREATE OR REPLACE VIEW vw_xrello_membresia AS
SELECT CONCAT(mp.proyecto_id, ':', mp.usuario_id) AS id,
       mp.proyecto_id                              AS proyecto_id,
       p.codigo                                     AS proyecto_codigo,
       p.estado                                      AS proyecto_estado,
       p.owner_id                                    AS owner_id,
       mp.usuario_id                                AS usuario_id,
       r.codigo                                      AS rol_codigo
  FROM cta_miembro_proyecto mp
  JOIN cta_proyecto p ON p.id = mp.proyecto_id
  JOIN cta_rol_proyecto r ON r.id = mp.rol_proyecto_id;
