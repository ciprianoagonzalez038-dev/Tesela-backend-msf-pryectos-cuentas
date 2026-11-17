-- ============================================================================
-- Proposito: renombrar la tabla de historial de Flyway de ms-xrello-cuentas
--            de su nombre por defecto (flyway_schema_history) al nombre
--            PROPIO de este servicio (flyway_schema_history_cuentas), ahora
--            que spring.flyway.table=flyway_schema_history_cuentas en
--            application.properties (ver XRELLO_CONTRATO.md, seccion
--            "FLYWAY EN ESQUEMA COMPARTIDO").
-- Fecha:     2026-09-29
-- Base de datos destino: db_xrello (la MISMA base fisica que comparte con
--            ms-xrello-tableros — nunca db_ms_products).
-- Ejecutado por: aprobacion explicita del usuario, UNA SOLA VEZ por entorno
--            (local / docker / cada ambiente real), ANTES de arrancar
--            ms-xrello-cuentas con la version de application.properties que
--            ya apunta al nombre nuevo — si se arranca antes de correr este
--            script, Flyway simplemente creara flyway_schema_history_cuentas
--            vacia y volvera a aplicar V1..V5 desde cero, lo cual FALLARA
--            porque las tablas cta_* ya existen ("Table 'cta_plan' already
--            exists"). Correr este script primero evita ese problema.
--
-- IMPORTANTE — por que esto es necesario: db_xrello es una base FISICA
-- compartida con ms-xrello-tableros. Con el nombre por defecto
-- (flyway_schema_history), ambos servicios escribirian su propio V1/V2/...
-- en la MISMA linea de versiones — una migracion de un servicio puede quedar
-- registrada bajo un numero que el otro servicio ya uso, o Flyway puede
-- llegar a asumir que una migracion ya esta aplicada sin haberla corrido
-- nunca. Cada servicio de Xrello necesita su PROPIA tabla de historial.
--
-- IDEMPOTENTE / SEGURO DE RE-EJECUTAR: el bloque de abajo solo actua si
-- flyway_schema_history (el nombre viejo) existe Y flyway_schema_history_cuentas
-- (el nombre nuevo) todavia NO existe. Si ya se corrio una vez, una segunda
-- ejecucion no hace nada (no falla, no duplica, no pisa datos).
-- ============================================================================

DELIMITER $$

CREATE PROCEDURE renombrar_historial_flyway_cuentas()
BEGIN
    IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'
        )
       AND NOT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history_cuentas'
        )
    THEN
        RENAME TABLE flyway_schema_history TO flyway_schema_history_cuentas;
    END IF;
END$$

DELIMITER ;

CALL renombrar_historial_flyway_cuentas();

DROP PROCEDURE renombrar_historial_flyway_cuentas;
