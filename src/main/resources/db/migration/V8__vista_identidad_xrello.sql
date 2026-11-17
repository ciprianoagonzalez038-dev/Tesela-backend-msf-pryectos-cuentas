-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Base de datos: db_xrello
-- Version:       V8 — vw_xrello_identidad
--
-- Vista de SOLO LECTURA que ms-xrello-tableros consulta para resolver al usuario LOCAL de un
-- token (por subject_externo del Authorization Server, o por correo para los tokens del emisor
-- local transitorio). Mismo patron que vw_xrello_membresia (V6, XRELLO_CONTRATO.md §19): un
-- servicio no lee tablas cta_* de otro, solo vistas publicadas; ms-xrello-tableros NO aprovisiona
-- usuarios, eso es solo de ms-xrello-cuentas.
--
-- Sin password_hash ni ninguna otra columna sensible. Columnas nuevas, SIEMPRE al final.
-- ============================================================================
CREATE OR REPLACE VIEW vw_xrello_identidad AS
SELECT u.id              AS usuario_id,
       u.subject_externo AS subject_externo,
       u.email           AS email,
       u.estado          AS estado
  FROM cta_usuario u;
