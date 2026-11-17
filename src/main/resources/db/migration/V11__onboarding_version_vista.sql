-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Base de datos: db_xrello
-- Version:       V11 — Recorrido de primera vez (onboarding) de app-xrello-web.
--                onboarding_version_vista = ultima version del recorrido que el
--                usuario vio u omitio (NULL = nunca). app-xrello-web la compara con
--                el parametro ONBOARDING_VERSION (ms-parametrizacion): si la vigente es
--                mayor, vuelve a abrir el recorrido una vez.
-- ============================================================================
ALTER TABLE cta_usuario ADD COLUMN onboarding_version_vista INT NULL;
