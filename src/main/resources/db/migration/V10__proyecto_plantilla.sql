-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Version:       V10 — cta_proyecto gana `plantilla_codigo`; vw_xrello_membresia
--                la expone AL FINAL (cambio aditivo, regla 3 de
--                V6__vista_membresia_xrello.sql).
--
-- POR QUE: el usuario puede elegir, al crear un proyecto, con qué plantilla de
-- ms-xrello-tableros se inicializa (DESARROLLO/SIMPLE, catálogo en ese
-- servicio — ver proyecto.InicializacionProyectoService y
-- domain/Plantilla.java allá). ms-xrello-cuentas crea el `Proyecto` pero no
-- conoce el catálogo de plantillas; ms-xrello-tableros inicializa sus
-- catálogos de forma PEREZOSA, en el primer acceso, nunca por una llamada
-- entre microservicios al crear el proyecto (ver XRELLO_CONTRATO.md §19). Por
-- eso la elección del usuario se guarda aquí y viaja al otro servicio por el
-- mismo canal ya establecido para esta clase de dato: extender
-- vw_xrello_membresia (mismo patrón que V9__vista_membresia_nombre.sql,
-- cuyo propio comentario ya preveía este caso: "si en el futuro se necesitara
-- resolver [algo] DESDE ms-xrello-tableros, la vía correcta es extender
-- vw_xrello_membresia").
--
-- NULL por diseño: significa exactamente lo mismo que no pasar ningún
-- codigoPlantilla a inicializarSiHaceFalta — usar la predeterminada. Los
-- proyectos ya existentes quedan con NULL sin necesitar backfill.
--
-- COORDINAR EN EL MISMO COMMIT (ver XRELLO_CONTRATO.md §19 regla 3): en
-- ms-xrello-tableros, membresia.MembresiaVista (campo nuevo), R__vw_xrello_
-- membresia_stub.sql (columna nueva en el stub de test) y
-- MembresiaVistaFormaTest (COLUMNAS_ESPERADAS apuntando a esta migración).
-- ============================================================================
ALTER TABLE cta_proyecto ADD COLUMN plantilla_codigo VARCHAR(40) NULL AFTER estado;

CREATE OR REPLACE VIEW vw_xrello_membresia AS
SELECT CONCAT(mp.proyecto_id, ':', mp.usuario_id) AS id,
       mp.proyecto_id                              AS proyecto_id,
       p.codigo                                     AS proyecto_codigo,
       p.estado                                      AS proyecto_estado,
       p.owner_id                                    AS owner_id,
       mp.usuario_id                                AS usuario_id,
       r.codigo                                      AS rol_codigo,
       u.nombre                                      AS nombre,
       p.plantilla_codigo                            AS plantilla_codigo
  FROM cta_miembro_proyecto mp
  JOIN cta_proyecto p ON p.id = mp.proyecto_id
  JOIN cta_rol_proyecto r ON r.id = mp.rol_proyecto_id
  JOIN cta_usuario u ON u.id = mp.usuario_id;
