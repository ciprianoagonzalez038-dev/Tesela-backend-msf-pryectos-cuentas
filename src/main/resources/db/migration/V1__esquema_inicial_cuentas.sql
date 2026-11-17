-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Base de datos: db_xrello (PROPIA — ver XRELLO_CONTRATO.md §3, NUNCA
--                db_ms_products)
-- Motor:         MySQL 8.0.45
-- Version:       V1 — Esquema inicial: auditoria de ms-commons + dominio de
--                cuentas/usuarios/proyectos/membresias/invitaciones.
--
-- Gestionado por Flyway (spring.flyway.locations=classpath:db/migration,
-- ver XRELLO_CONTRATO.md §7). spring.jpa.hibernate.ddl-auto=validate SIEMPRE:
-- este script es la UNICA fuente de verdad del esquema.
-- ============================================================================


-- ============================================================================
-- 1. auditoria_datos (ms-commons) — auditoria automatica de INSERT/UPDATE/
--    DELETE de entidades anotadas con @CustomAuditDataAuditable
--    (com.springboot.cloud.cagr.commons.audit.data.entities.CustomAuditDataEntry).
--
--    app.audit.enabled=true en application.properties activa
--    CustomAuditDataAutoConfiguration (ms-commons) en cuanto haya un
--    EntityManagerFactory en el contexto — ddl-auto=validate exige que esta
--    tabla ya exista ANTES de arrancar (ver ms-commons/CLAUDE.md §3.7,
--    "Prerequisito de base de datos").
--
--    DDL DERIVADO EMPIRICAMENTE de la entidad real (no inventado): se generó
--    con Hibernate 6.6.49.Final + MySQLDialect (la misma combinacion que usa
--    este proyecto) el DDL exacto que produce CustomAuditDataEntry, en vez de
--    copiar a ciegas el de otro microservicio del ecosistema (ver ms-products/
--    V2__create_auditoria_datos.sql, que usa VARCHAR(10) para tipo_accion).
--
--    HALLAZGO IMPORTANTE: Hibernate 6.x, con MySQLDialect, mapea un campo
--    @Enumerated(EnumType.STRING) a un tipo ENUM NATIVO de MySQL (no a
--    VARCHAR) — CustomAuditDataEntry.tipoAccion es exactamente ese caso.
--    VARCHAR(10) (el tipo que documenta ms-products) NO habria pasado
--    ddl-auto=validate en este proyecto (Hibernate esperaria
--    "enum('DELETE','INSERT','UPDATE')" y reportaria "wrong column type").
--    El orden alfabetico de los valores del ENUM (DELETE, INSERT, UPDATE — NO
--    el orden de declaracion del enum Java, que es INSERT, UPDATE, DELETE) es
--    el que Hibernate genera y espera; se respeta tal cual aqui.
--
--    Columna               | Tipo Java             | Tipo MySQL exacto (Hibernate)
--    ----------------------|-----------------------|--------------------------------
--    id                    | Long                  | BIGINT, PK, AUTO_INCREMENT
--    tipo_accion           | CustomAuditDataAction | ENUM('DELETE','INSERT','UPDATE') NOT NULL
--    tabla_afectada        | String                | VARCHAR(100) NOT NULL
--    id_registro_afectado  | String                | VARCHAR(100) NULL
--    valor_anterior        | String (JSON)         | LONGTEXT NULL
--    valor_nuevo           | String (JSON)         | LONGTEXT NULL
--    usuario               | String                | VARCHAR(150) NULL
--    ip_origen             | String                | VARCHAR(45) NULL
--    fecha_evento          | LocalDateTime         | DATETIME(6) NOT NULL
-- ============================================================================
CREATE TABLE auditoria_datos (
    id                    BIGINT                              NOT NULL AUTO_INCREMENT,
    tipo_accion           ENUM('DELETE','INSERT','UPDATE')    NOT NULL,
    tabla_afectada        VARCHAR(100)                        NOT NULL,
    id_registro_afectado  VARCHAR(100)                        NULL,
    valor_anterior        LONGTEXT                            NULL,
    valor_nuevo           LONGTEXT                            NULL,
    usuario               VARCHAR(150)                        NULL,
    ip_origen             VARCHAR(45)                         NULL,
    fecha_evento          DATETIME(6)                         NOT NULL,

    CONSTRAINT pk_auditoria_datos PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Consulta tipica: historial de auditoria de un registro concreto.
CREATE INDEX ix_auditoria_datos_tabla_registro ON auditoria_datos (tabla_afectada, id_registro_afectado);


-- ============================================================================
-- 2. cta_plan — planes de suscripcion (hoy solo FREE, ver
--    V2__datos_semilla_cuentas.sql).
-- ============================================================================
CREATE TABLE cta_plan (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    codigo         VARCHAR(40)  NOT NULL,
    nombre         VARCHAR(120) NOT NULL,
    activo         BIT(1)       NOT NULL DEFAULT b'1',
    creado_en      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_plan PRIMARY KEY (id),
    CONSTRAINT uq_cta_plan_codigo UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================================
-- 3. cta_limite — catalogo de limites configurables por plan/usuario (ej.
--    PROYECTOS_COMO_OWNER, ver V2__datos_semilla_cuentas.sql).
-- ============================================================================
CREATE TABLE cta_limite (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    codigo         VARCHAR(60)  NOT NULL,
    descripcion    VARCHAR(200) NULL,
    creado_en      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_limite PRIMARY KEY (id),
    CONSTRAINT uq_cta_limite_codigo UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================================
-- 4. cta_rol_proyecto — catalogo de roles dentro de un proyecto (OWNER,
--    ADMIN, MIEMBRO, OBSERVADOR — ver V2__datos_semilla_cuentas.sql).
-- ============================================================================
CREATE TABLE cta_rol_proyecto (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    codigo         VARCHAR(30) NOT NULL,
    nombre         VARCHAR(80) NOT NULL,
    orden          INT         NOT NULL,
    creado_en      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_rol_proyecto PRIMARY KEY (id),
    CONSTRAINT uq_cta_rol_proyecto_codigo UNIQUE (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================================
-- 5. cta_usuario — cuenta de usuario de Xrello.
--    Sin ON DELETE explicito en fk_cta_usuario_plan: RESTRICT por defecto de
--    InnoDB — un plan con usuarios activos no puede borrarse (no se pidio
--    cascada).
-- ============================================================================
CREATE TABLE cta_usuario (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(180) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    nombre            VARCHAR(120) NOT NULL,
    estado            VARCHAR(20)  NOT NULL,
    plan_id           BIGINT       NOT NULL,
    email_verificado  BIT(1)       NOT NULL DEFAULT b'0',
    ultimo_acceso     DATETIME(6)  NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    creado_en         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_usuario PRIMARY KEY (id),
    CONSTRAINT uq_cta_usuario_email UNIQUE (email),
    CONSTRAINT fk_cta_usuario_plan FOREIGN KEY (plan_id) REFERENCES cta_plan (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================================
-- 6. cta_plan_limite — valor de cada limite para cada plan. valor NULL =
--    ilimitado (ver XRELLO_CONTRATO.md, sin FK cruzando microservicios: esta
--    tabla solo referencia catalogos propios de este mismo servicio).
-- ============================================================================
CREATE TABLE cta_plan_limite (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    plan_id        BIGINT      NOT NULL,
    limite_id      BIGINT      NOT NULL,
    valor          INT         NULL,
    creado_en      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_plan_limite PRIMARY KEY (id),
    CONSTRAINT uq_cta_plan_limite_plan_limite UNIQUE (plan_id, limite_id),
    CONSTRAINT fk_cta_plan_limite_plan FOREIGN KEY (plan_id) REFERENCES cta_plan (id),
    CONSTRAINT fk_cta_plan_limite_limite FOREIGN KEY (limite_id) REFERENCES cta_limite (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================================
-- 7. cta_limite_usuario — override puntual de un limite para un usuario
--    concreto (por encima o por debajo del valor de su plan). valor NULL =
--    ilimitado.
-- ============================================================================
CREATE TABLE cta_limite_usuario (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id     BIGINT      NOT NULL,
    limite_id      BIGINT      NOT NULL,
    valor          INT         NULL,
    creado_en      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_limite_usuario PRIMARY KEY (id),
    CONSTRAINT uq_cta_limite_usuario_usuario_limite UNIQUE (usuario_id, limite_id),
    CONSTRAINT fk_cta_limite_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES cta_usuario (id),
    CONSTRAINT fk_cta_limite_usuario_limite FOREIGN KEY (limite_id) REFERENCES cta_limite (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================================
-- 8. cta_proyecto — proyecto/tablero Kanban. codigo es un UUID asignado en
--    Java (igual que pr_instancia en ms-onboarding, ver su CLAUDE.md) — nunca
--    se expone el id numerico interno. prefijo es el prefijo corto usado para
--    numerar items (ej. "XRL-1"), unico por owner (dos proyectos del mismo
--    dueño no pueden compartir prefijo; distintos duenos si pueden).
-- ============================================================================
CREATE TABLE cta_proyecto (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    codigo             CHAR(36)     NOT NULL,
    nombre             VARCHAR(160) NOT NULL,
    descripcion        VARCHAR(500) NULL,
    prefijo            VARCHAR(10)  NOT NULL,
    consecutivo_item   BIGINT       NOT NULL DEFAULT 0,
    owner_id           BIGINT       NOT NULL,
    miembros_actuales  INT          NOT NULL DEFAULT 0,
    estado             VARCHAR(20)  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    creado_en          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_proyecto PRIMARY KEY (id),
    CONSTRAINT uq_cta_proyecto_codigo UNIQUE (codigo),
    CONSTRAINT uq_cta_proyecto_owner_prefijo UNIQUE (owner_id, prefijo),
    CONSTRAINT fk_cta_proyecto_owner FOREIGN KEY (owner_id) REFERENCES cta_usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================================
-- 9. cta_miembro_proyecto — membresia de un usuario en un proyecto, con su
--    rol. invitado_por es NULL para el owner (se agrega solo al crear el
--    proyecto, sin invitacion) y NOT NULL para el resto (referencia a quien
--    genero/acepto la invitacion). ix_cta_miembro_proyecto_usuario es
--    EXPLICITO (no basta el indice implicito que InnoDB crearia para la FK
--    usuario_id, que ya queda cubierto por igual): la consulta del Home
--    ("proyectos en los que participo") filtra por usuario_id solo, y el
--    UNIQUE (proyecto_id, usuario_id) no sirve para ese acceso porque
--    usuario_id no es su columna lider.
-- ============================================================================
CREATE TABLE cta_miembro_proyecto (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    proyecto_id      BIGINT      NOT NULL,
    usuario_id       BIGINT      NOT NULL,
    rol_proyecto_id  BIGINT      NOT NULL,
    invitado_por     BIGINT      NULL,
    fecha_ingreso    DATETIME(6) NOT NULL,
    creado_en        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_miembro_proyecto PRIMARY KEY (id),
    CONSTRAINT uq_cta_miembro_proyecto_proyecto_usuario UNIQUE (proyecto_id, usuario_id),
    CONSTRAINT fk_cta_miembro_proyecto_proyecto FOREIGN KEY (proyecto_id) REFERENCES cta_proyecto (id),
    CONSTRAINT fk_cta_miembro_proyecto_usuario FOREIGN KEY (usuario_id) REFERENCES cta_usuario (id),
    CONSTRAINT fk_cta_miembro_proyecto_rol_proyecto FOREIGN KEY (rol_proyecto_id) REFERENCES cta_rol_proyecto (id),
    CONSTRAINT fk_cta_miembro_proyecto_invitado_por FOREIGN KEY (invitado_por) REFERENCES cta_usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_cta_miembro_proyecto_usuario ON cta_miembro_proyecto (usuario_id);


-- ============================================================================
-- 10. cta_invitacion — invitacion a un proyecto. token_hash es el SHA-256 en
--     hex (64 caracteres) del token real: el token en claro NUNCA se
--     almacena, mismo patron que pr_evento_token en ms-onboarding (ver su
--     CLAUDE.md, "Invalidacion de tokens").
--     tipo: ENLACE_ABIERTO (cualquiera con el link puede unirse, sin
--     email_destino) | EMAIL_DIRIGIDO (dirigida a un correo concreto).
-- ============================================================================
CREATE TABLE cta_invitacion (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    codigo            CHAR(36)     NOT NULL,
    proyecto_id       BIGINT       NOT NULL,
    token_hash        CHAR(64)     NOT NULL,
    tipo              VARCHAR(20)  NOT NULL,
    email_destino     VARCHAR(180) NULL,
    rol_asignado_id   BIGINT       NOT NULL,
    creada_por        BIGINT       NOT NULL,
    fecha_expiracion  DATETIME(6)  NOT NULL,
    usos_maximos      INT          NOT NULL,
    usos_actuales     INT          NOT NULL DEFAULT 0,
    estado            VARCHAR(20)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    creado_en         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_invitacion PRIMARY KEY (id),
    CONSTRAINT uq_cta_invitacion_codigo UNIQUE (codigo),
    CONSTRAINT uq_cta_invitacion_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_cta_invitacion_proyecto FOREIGN KEY (proyecto_id) REFERENCES cta_proyecto (id),
    CONSTRAINT fk_cta_invitacion_rol_asignado FOREIGN KEY (rol_asignado_id) REFERENCES cta_rol_proyecto (id),
    CONSTRAINT fk_cta_invitacion_creada_por FOREIGN KEY (creada_por) REFERENCES cta_usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================================
-- 11. cta_invitacion_uso — registro de cada usuario que consumio una
--     invitacion (una invitacion ENLACE_ABIERTO puede tener usos_maximos > 1;
--     el UNIQUE evita que el mismo usuario cuente dos veces contra ese tope).
-- ============================================================================
CREATE TABLE cta_invitacion_uso (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    invitacion_id  BIGINT      NOT NULL,
    usuario_id     BIGINT      NOT NULL,
    fecha          DATETIME(6) NOT NULL,
    creado_en      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cta_invitacion_uso PRIMARY KEY (id),
    CONSTRAINT uq_cta_invitacion_uso_invitacion_usuario UNIQUE (invitacion_id, usuario_id),
    CONSTRAINT fk_cta_invitacion_uso_invitacion FOREIGN KEY (invitacion_id) REFERENCES cta_invitacion (id),
    CONSTRAINT fk_cta_invitacion_uso_usuario FOREIGN KEY (usuario_id) REFERENCES cta_usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
