package com.springboot.cloud.cagr.xrello.cuentas.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Membresia de un {@link Usuario} en un {@link Proyecto}, con su
 * {@link RolProyecto}. {@code invitadoPor} es {@code NULL} para el owner (se
 * agrega solo, sin invitacion, al crear el proyecto) y no nulo para el resto
 * de miembros (referencia a quien genero/acepto la invitacion).
 */
@Entity
@Table(name = "cta_miembro_proyecto")
@Getter
@Setter
@NoArgsConstructor
public class MiembroProyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_proyecto_id", nullable = false)
    private RolProyecto rolProyecto;

    /** {@code NULL} solo para el owner del proyecto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitado_por")
    private Usuario invitadoPor;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDateTime fechaIngreso;

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
        if (!(o instanceof MiembroProyecto other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
