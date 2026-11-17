-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Base de datos: db_xrello
-- Version:       V5 — Roles de SISTEMA (no confundir con cta_rol_proyecto,
--                que son roles POR PROYECTO). Un rol de sistema es propio del
--                usuario, independiente de cualquier proyecto, y SI viaja
--                dentro del JWT (claim "roles") — ver
--                security.TokenService/SecurityConfig. Ningun rol de
--                proyecto viaja nunca en el token (ver XRELLO_CONTRATO.md).
--
-- Semillas: SOPORTE (rutas administrativas de diagnostico/reconciliacion) y
-- AUDITOR (solo lectura sobre rutas administrativas futuras) — ninguna se
-- asigna a ningun usuario por esta migracion; la asignacion es manual.
-- ============================================================================
CREATE TABLE cta_rol_sistema (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    codigo         VARCHAR(30)  NOT NULL,
    nombre         VARCHAR(80)  NOT NULL,
    creado_en      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_rol_sistema PRIMARY KEY (id),
    CONSTRAINT uq_cta_rol_sistema_codigo UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE cta_usuario_rol_sistema (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id     BIGINT      NOT NULL,
    rol_sistema_id BIGINT      NOT NULL,
    creado_en      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_usuario_rol_sistema PRIMARY KEY (id),
    CONSTRAINT uq_cta_usuario_rol_sistema_usuario_rol UNIQUE (usuario_id, rol_sistema_id),
    CONSTRAINT fk_cta_usuario_rol_sistema_usuario FOREIGN KEY (usuario_id) REFERENCES cta_usuario (id),
    CONSTRAINT fk_cta_usuario_rol_sistema_rol FOREIGN KEY (rol_sistema_id) REFERENCES cta_rol_sistema (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


INSERT IGNORE INTO cta_rol_sistema (codigo, nombre) VALUES
    ('SOPORTE', 'Soporte'),
    ('AUDITOR', 'Auditor');
