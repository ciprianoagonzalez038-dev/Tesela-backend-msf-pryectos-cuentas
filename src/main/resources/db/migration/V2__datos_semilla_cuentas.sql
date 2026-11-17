-- ============================================================================
-- Microservicio: ms-xrello-cuentas
-- Base de datos: db_xrello
-- Version:       V2 — Datos semilla de catalogos (plan FREE, limites, roles
--                de proyecto). Todas las inserciones son IDEMPOTENTES
--                (INSERT IGNORE sobre columnas con restriccion UNIQUE): si
--                esta migracion se re-ejecutara sobre una base que ya la
--                tiene aplicada, no falla ni duplica filas.
-- ============================================================================


-- ============================================================================
-- 1. Plan FREE — unico plan existente hoy.
-- ============================================================================
INSERT IGNORE INTO cta_plan (codigo, nombre, activo)
VALUES ('FREE', 'Free', b'1');


-- ============================================================================
-- 2. Catalogo de limites configurables (por plan y, opcionalmente, por
--    usuario via cta_limite_usuario).
-- ============================================================================
INSERT IGNORE INTO cta_limite (codigo, descripcion) VALUES
    ('PROYECTOS_COMO_OWNER',    'Cantidad maxima de proyectos que un usuario puede poseer como owner'),
    ('PROYECTOS_COMO_INVITADO', 'Cantidad maxima de proyectos en los que un usuario puede participar como invitado'),
    ('PROYECTOS_TOTAL',         'Cantidad maxima total de proyectos (como owner + como invitado) en los que un usuario puede participar'),
    ('MIEMBROS_POR_PROYECTO',   'Cantidad maxima de miembros que puede tener un proyecto');


-- ============================================================================
-- 3. Valores del plan FREE para cada limite: 5 / 10 / 10 / 8.
--    JOIN por codigo (no por id) — el id autogenerado puede variar entre
--    ambientes; el codigo es la clave estable.
-- ============================================================================
INSERT IGNORE INTO cta_plan_limite (plan_id, limite_id, valor)
SELECT p.id, l.id, 5
FROM cta_plan p
JOIN cta_limite l ON l.codigo = 'PROYECTOS_COMO_OWNER'
WHERE p.codigo = 'FREE';

INSERT IGNORE INTO cta_plan_limite (plan_id, limite_id, valor)
SELECT p.id, l.id, 10
FROM cta_plan p
JOIN cta_limite l ON l.codigo = 'PROYECTOS_COMO_INVITADO'
WHERE p.codigo = 'FREE';

INSERT IGNORE INTO cta_plan_limite (plan_id, limite_id, valor)
SELECT p.id, l.id, 10
FROM cta_plan p
JOIN cta_limite l ON l.codigo = 'PROYECTOS_TOTAL'
WHERE p.codigo = 'FREE';

INSERT IGNORE INTO cta_plan_limite (plan_id, limite_id, valor)
SELECT p.id, l.id, 8
FROM cta_plan p
JOIN cta_limite l ON l.codigo = 'MIEMBROS_POR_PROYECTO'
WHERE p.codigo = 'FREE';


-- ============================================================================
-- 4. Roles de proyecto, en orden de mayor a menor privilegio.
-- ============================================================================
INSERT IGNORE INTO cta_rol_proyecto (codigo, nombre, orden) VALUES
    ('OWNER',      'Owner',         1),
    ('ADMIN',      'Administrador', 2),
    ('MIEMBRO',    'Miembro',       3),
    ('OBSERVADOR', 'Observador',    4);
