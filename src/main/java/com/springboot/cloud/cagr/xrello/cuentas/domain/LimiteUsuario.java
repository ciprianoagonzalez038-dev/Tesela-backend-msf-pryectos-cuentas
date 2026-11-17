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
 * Override puntual de un {@link Limite} para un {@link Usuario} concreto, por
 * encima o por debajo del valor que le da su {@link Plan} via
 * {@link PlanLimite}. {@code valor} {@code NULL} significa ilimitado.
 */
@Entity
@Table(name = "cta_limite_usuario")
@Getter
@Setter
@NoArgsConstructor
public class LimiteUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "limite_id", nullable = false)
    private Limite limite;

    /** {@code NULL} = ilimitado. */
    @Column(name = "valor")
    private Integer valor;

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
        if (!(o instanceof LimiteUsuario other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
