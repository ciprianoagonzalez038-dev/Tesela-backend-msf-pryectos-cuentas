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
 * Catalogo de limites configurables del ecosistema Xrello (ej.
 * {@code PROYECTOS_COMO_OWNER}, ver {@code V2__datos_semilla_cuentas.sql}).
 * El valor concreto de cada limite vive en {@link PlanLimite} (por plan) o en
 * {@link LimiteUsuario} (override puntual por usuario), nunca en esta clase.
 */
@Entity
@Table(name = "cta_limite")
@Getter
@Setter
@NoArgsConstructor
public class Limite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo", length = 60, nullable = false, unique = true)
    private String codigo;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

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
        if (!(o instanceof Limite other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
