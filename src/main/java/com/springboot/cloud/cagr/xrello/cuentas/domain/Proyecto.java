package com.springboot.cloud.cagr.xrello.cuentas.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Proyecto/tablero Kanban. {@code codigo} es un UUID asignado en Java (nunca
 * se expone el {@code id} numerico interno) — mismo patron que
 * {@code pr_instancia} en {@code ms-onboarding}.
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.CHAR)}: sin esta anotacion, Hibernate
 * espera {@code VARCHAR(36)} para un {@link String} con {@code length = 36} y
 * {@code ddl-auto=validate} rechaza la columna real
 * {@code CHAR(36)} de {@code V1__esquema_inicial_cuentas.sql} con
 * {@code "wrong column type"} (verificado empiricamente). Esta anotacion le
 * dice a Hibernate que la columna es {@code CHAR}, no {@code VARCHAR}.</p>
 *
 * <p>{@code prefijo} es unico por {@code owner} ({@code uq_cta_proyecto_owner_prefijo}
 * en el esquema), no globalmente: dos duenos distintos pueden usar el mismo
 * prefijo corto para numerar sus items (ej. {@code "XRL-1"}).</p>
 *
 * <p>{@code @Version}: un proyecto se edita concurrentemente (nombre,
 * descripcion, estado) — ver XRELLO_CONTRATO.md §6.</p>
 */
@Entity
@Table(name = "cta_proyecto")
@Getter
@Setter
@NoArgsConstructor
public class Proyecto {

    /** Cuenta para los limites de cupo (PROYECTOS_COMO_OWNER/COMO_INVITADO/TOTAL/MIEMBROS_POR_PROYECTO). */
    public static final String ESTADO_ACTIVO = "ACTIVO";

    /** No cuenta para ningun limite de cupo — ver {@code limite.LimiteService}. */
    public static final String ESTADO_ARCHIVADO = "ARCHIVADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "codigo", length = 36, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", length = 160, nullable = false)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "prefijo", length = 10, nullable = false)
    private String prefijo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Usuario owner;

    @Column(name = "miembros_actuales", nullable = false)
    private Integer miembrosActuales;

    /**
     * String plano, nunca un enum Java con {@code @Enumerated} — ver Javadoc
     * de {@link Usuario} para la razon (ENUM nativo de MySQL vs VARCHAR(20)
     * real de esta columna).
     */
    @Column(name = "estado", length = 20, nullable = false)
    private String estado;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Código de plantilla ({@code DESARROLLO}/{@code SIMPLE}, catálogo en ms-xrello-tableros) con
     * el que el dueño eligió inicializar el proyecto. {@code NULL} = no eligió ninguna — equivale
     * a pasar {@code null} a {@code InicializacionProyectoService#inicializarSiHaceFalta}, que usa
     * la plantilla predeterminada. Viaja a ms-xrello-tableros SOLO vía {@code vw_xrello_membresia}
     * (ver {@code V10__proyecto_plantilla.sql}) — ms-xrello-cuentas nunca valida contra el catálogo
     * real de plantillas (vive en otro microservicio, sin FK física, ver XRELLO_CONTRATO.md §19).
     */
    @Column(name = "plantilla_codigo", length = 40)
    private String plantillaCodigo;

    /** Puesto por MySQL (DEFAULT CURRENT_TIMESTAMP(6)); no editable desde Java. */
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Puesto/mantenido por MySQL (DEFAULT ... ON UPDATE CURRENT_TIMESTAMP(6)). */
    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Proyecto other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
