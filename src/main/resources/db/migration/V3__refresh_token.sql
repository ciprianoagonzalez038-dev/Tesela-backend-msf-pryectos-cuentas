-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Base de datos: db_xrello
-- Version:       V3 — Refresh tokens opacos (autenticacion JWT, ver
--                XRELLO_CONTRATO.md y security/).
--
-- Solo se almacena el SHA-256 en hex del token (token_hash) — el token en
-- claro se genera con SecureRandom (32 bytes, Base64 URL-safe) y se devuelve
-- UNA sola vez en la respuesta de login/refresh; nunca se persiste.
--
-- Sin actualizado_en a proposito (a diferencia del resto de tablas cta_): el
-- unico cambio que sufre una fila tras crearse es revocado 0->1 (rotacion o
-- deteccion de reuso), y no hace falta saber CUANDO paso eso, solo QUE paso.
-- ============================================================================
CREATE TABLE cta_refresh_token (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    token_hash       CHAR(64)    NOT NULL,
    usuario_id       BIGINT      NOT NULL,
    fecha_expiracion DATETIME(6) NOT NULL,
    revocado         BIT(1)      NOT NULL DEFAULT b'0',
    creado_en        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_refresh_token PRIMARY KEY (id),
    CONSTRAINT uq_cta_refresh_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_cta_refresh_token_usuario FOREIGN KEY (usuario_id) REFERENCES cta_usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Consulta tipica: "revocar TODOS los refresh tokens de este usuario" (deteccion de reuso).
CREATE INDEX ix_cta_refresh_token_usuario ON cta_refresh_token (usuario_id);
