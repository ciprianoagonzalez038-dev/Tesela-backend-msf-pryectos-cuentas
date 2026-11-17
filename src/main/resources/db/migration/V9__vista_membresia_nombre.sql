-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Version:       V9 — vw_xrello_membresia gana la columna `nombre` (de
--                cta_usuario.nombre) AL FINAL de la proyección.
--
-- POR QUE: ms-xrello-tableros necesita resolver menciones "@Nombre" en
-- comentarios contra los miembros REALES del proyecto (ver
-- comentario.ComentarioService, tarea de comentarios/checklists). Ese
-- servicio nunca llama por HTTP a ms-xrello-cuentas para resolver identidad
-- (ver XRELLO_CONTRATO.md §19) — la via es SIEMPRE extender una vista ya
-- publicada, nunca una llamada remota en el camino caliente. Ya estaba
-- documentado como el camino correcto en XRELLO_ESTADO_BACKEND.md ("tableros
-- no puede resolver nombres de usuario por si mismo"): "si en el futuro se
-- necesitara resolver nombres DESDE ms-xrello-tableros, la via correcta es
-- extender vw_xrello_membresia con una columna de nombre".
--
-- CAMBIO ADITIVO (regla 3 de V6__vista_membresia_xrello.sql): columna nueva
-- AL FINAL, ningun consumidor existente de la vista se rompe. Coordinar con
-- ms-xrello-tableros en el MISMO commit: su entidad JPA de solo lectura
-- (membresia.MembresiaVista), el stub de test
-- (R__vw_xrello_membresia_stub.sql) y membresia.MembresiaVistaFormaTest
-- (COLUMNAS_ESPERADAS) deben actualizarse junto con esta migracion.
-- ============================================================================
CREATE OR REPLACE VIEW vw_xrello_membresia AS
SELECT CONCAT(mp.proyecto_id, ':', mp.usuario_id) AS id,
       mp.proyecto_id                              AS proyecto_id,
       p.codigo                                     AS proyecto_codigo,
       p.estado                                      AS proyecto_estado,
       p.owner_id                                    AS owner_id,
       mp.usuario_id                                AS usuario_id,
       r.codigo                                      AS rol_codigo,
       u.nombre                                      AS nombre
  FROM cta_miembro_proyecto mp
  JOIN cta_proyecto p ON p.id = mp.proyecto_id
  JOIN cta_rol_proyecto r ON r.id = mp.rol_proyecto_id
  JOIN cta_usuario u ON u.id = mp.usuario_id;
