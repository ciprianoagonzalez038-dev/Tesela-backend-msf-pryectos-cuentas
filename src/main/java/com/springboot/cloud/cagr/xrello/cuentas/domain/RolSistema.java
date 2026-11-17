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
 * Catalogo de roles de SISTEMA (ver {@code V5__rol_sistema.sql}) — propios del usuario,
 * independientes de cualquier proyecto. No confundir con {@link RolProyecto}: un rol de sistema
 * SI viaja dentro del JWT (claim {@code roles}, ver {@code security.TokenService}); un rol de
 * proyecto NUNCA viaja en el token (se resuelve en cada request via
 * {@code security.AccesoProyecto}).
 */
@Entity
@Table(name = "cta_rol_sistema")
@Getter
@Setter
@NoArgsConstructor
public class RolSistema {

    public static final String ROL_SOPORTE = "SOPORTE";

    public static final String ROL_AUDITOR = "AUDITOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo", length = 30, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", length = 80, nullable = false)
    private String nombre;

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
        if (!(o instanceof RolSistema other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
