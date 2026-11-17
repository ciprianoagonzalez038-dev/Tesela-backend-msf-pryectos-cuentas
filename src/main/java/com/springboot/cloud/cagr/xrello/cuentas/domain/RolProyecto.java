package com.springboot.cloud.cagr.xrello.cuentas.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catalogo de roles dentro de un {@link Proyecto} (OWNER, ADMIN, MIEMBRO,
 * OBSERVADOR — ver {@code V2__datos_semilla_cuentas.sql}), en orden de mayor
 * a menor privilegio ({@link #orden}).
 */
@Entity
@Table(name = "cta_rol_proyecto")
@Getter
@Setter
@NoArgsConstructor
public class RolProyecto {

    /** Unico rol asignado al crear un proyecto; solo cambia de mano via transferirPropiedad. */
    public static final String ROL_OWNER = "OWNER";

    public static final String ROL_ADMIN = "ADMIN";

    public static final String ROL_MIEMBRO = "MIEMBRO";

    public static final String ROL_OBSERVADOR = "OBSERVADOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo", length = 30, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", length = 80, nullable = false)
    private String nombre;

    @Column(name = "orden", nullable = false)
    private Integer orden;

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
        if (!(o instanceof RolProyecto other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
