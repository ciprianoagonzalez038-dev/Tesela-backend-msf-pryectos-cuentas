-- ============================================================================
-- Proposito: crear la base de datos PROPIA de ms-xrello-cuentas (db_xrello,
--            NUNCA compartida con db_ms_products — ver XRELLO_CONTRATO.md §3)
--            y el usuario dedicado con el que este microservicio se conecta
--            (NUNCA root).
-- Fecha:     2026-09-28
-- Base de datos destino: instancia MySQL 8.0.45 del ecosistema (servidor,
--            no una base en particular — este script crea la base).
-- Ejecutado por: aprobacion explicita del usuario, una sola vez por entorno
--            (local / docker / cada ambiente real), antes de que Flyway
--            pueda aplicar la primera migracion (V1__*.sql) contra db_xrello.
--
-- IMPORTANTE: reemplazar 'CAMBIAR_ESTA_CLAVE' por una contraseña real antes de
-- ejecutar. La contraseña de desarrollo local documentada en
-- application-local.properties/application-docker.properties
-- ('xrello_local_2026') es SOLO un default de desarrollo — en cualquier
-- ambiente real, usar una contraseña distinta y pasarla via variable de
-- entorno (DB_PASSWORD), nunca reescribir este archivo con ella.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS db_xrello
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Usuario dedicado de ms-xrello-cuentas (y, mas adelante, ms-xrello-tableros
-- si comparte la misma base — a confirmar cuando ese servicio se cree). '%'
-- porque tambien se conecta desde el contenedor Docker via host.docker.internal.
CREATE USER IF NOT EXISTS 'xrello'@'%' IDENTIFIED BY 'CAMBIAR_ESTA_CLAVE';

-- Privilegios acotados a db_xrello — nunca GRANT ALL sobre *.* (a diferencia
-- del usuario root que hoy comparte el resto del ecosistema sobre
-- db_ms_products, ver ANALISIS_PLANTILLAS.md §12, riesgo 15).
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
    ON db_xrello.*
    TO 'xrello'@'%';

FLUSH PRIVILEGES;
