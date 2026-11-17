-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Base de datos: db_xrello
-- Version:       V4 — Prepara cta_usuario para la migracion al Authorization
--                Server OAuth2 centralizado del ecosistema (ver
--                XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2"):
--
--                1. subject_externo: guardara el claim "sub" que emita el
--                   futuro Authorization Server, para resolver el Usuario
--                   local por ese identificador externo (aprovisionamiento
--                   just-in-time, ver security.UsuarioActual).
--                2. password_hash pasa a NULL-able: un usuario aprovisionado
--                   desde el Authorization Server no tendra password local
--                   (la autenticacion la resuelve el Authorization Server,
--                   no BCrypt contra esta tabla).
--
--                El login/registro local (TRANSITORIO) sigue funcionando
--                exactamente igual hasta que se apague: ningun usuario
--                existente pierde su password_hash ni gana un valor en
--                subject_externo con esta migracion.
-- ============================================================================

ALTER TABLE cta_usuario
    ADD COLUMN subject_externo VARCHAR(255) NULL,
    ADD CONSTRAINT uq_usuario_subject_externo UNIQUE (subject_externo);

ALTER TABLE cta_usuario
    MODIFY COLUMN password_hash VARCHAR(100) NULL;
