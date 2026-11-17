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
 * Plan de suscripcion de una {@link Usuario cuenta}. Hoy solo existe el plan
 * {@code FREE} (ver {@code V2__datos_semilla_cuentas.sql}). Sus limites por
 * defecto viven en {@link PlanLimite}, no en esta clase.
 */
@Entity
@Table(name = "cta_plan")
@Getter
@Setter
@NoArgsConstructor
public class Plan {

    /** Unico plan hoy — asignado por defecto a toda cuenta nueva (ver {@code RegistroService}). */
    public static final String CODIGO_FREE = "FREE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo", length = 40, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

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
        if (!(o instanceof Plan other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
