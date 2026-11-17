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
 * Invitacion a un {@link Proyecto}. {@code tokenHash} es el SHA-256 en hex
 * (64 caracteres) del token real — el token en claro NUNCA se almacena, mismo
 * patron que {@code pr_evento_token} en {@code ms-onboarding}.
 *
 * <p>{@code tipo}: {@code ENLACE_ABIERTO} (cualquiera con el link puede
 * unirse, sin {@code emailDestino}) o {@code EMAIL_DIRIGIDO} (dirigida a un
 * correo concreto). Se mapea como {@link String} plano, no como enum Java —
 * misma razon documentada en {@link Usuario}.</p>
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.CHAR)} en {@code codigo}/{@code tokenHash}:
 * ver Javadoc de {@link Proyecto} para por que hace falta contra columnas
 * {@code CHAR} reales.</p>
 *
 * <p>{@code @Version}: una invitacion se edita concurrentemente (estado,
 * usos_actuales) — ver XRELLO_CONTRATO.md §6.</p>
 */
@Entity
@Table(name = "cta_invitacion")
@Getter
@Setter
@NoArgsConstructor
public class Invitacion {

    public static final String ESTADO_ACTIVA = "ACTIVA";
    public static final String ESTADO_REVOCADA = "REVOCADA";

    /**
     * Todos los usos fueron consumidos ({@code usosActuales == usosMaximos}). La fija el mismo {@code UPDATE} atomico
     * que ocupa el ultimo uso ({@code InvitacionRepository#ocuparUso}) y la deshace {@code liberarUso} si ese uso se
     * compensa. Una invitacion AGOTADA ya no se puede usar ni revocar desde la UI; sigue en la lista como historial.
     */
    public static final String ESTADO_AGOTADA = "AGOTADA";

    /** Cualquiera con el link puede unirse; {@code emailDestino} queda NULL. */
    public static final String TIPO_ENLACE_ABIERTO = "ENLACE_ABIERTO";

    /** Dirigida a un correo concreto; fuerza {@code usosMaximos = 1} (ver {@code InvitacionService}). */
    public static final String TIPO_EMAIL_DIRIGIDO = "EMAIL_DIRIGIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "codigo", length = 36, nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "tipo", length = 20, nullable = false)
    private String tipo;

    /** Solo aplica cuando {@code tipo == EMAIL_DIRIGIDO}. */
    @Column(name = "email_destino", length = 180)
    private String emailDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_asignado_id", nullable = false)
    private RolProyecto rolAsignado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creada_por", nullable = false)
    private Usuario creadaPor;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "usos_maximos", nullable = false)
    private Integer usosMaximos;

    @Column(name = "usos_actuales", nullable = false)
    private Integer usosActuales;

    /** String plano, nunca un enum Java — ver Javadoc de clase. */
    @Column(name = "estado", length = 20, nullable = false)
    private String estado;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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
        if (!(o instanceof Invitacion other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
