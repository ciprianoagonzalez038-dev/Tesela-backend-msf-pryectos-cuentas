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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// TRANSITORIO: migra al Authorization Server — la tabla cta_refresh_token y esta entidad
// desaparecen cuando el ecosistema tenga un Authorization Server OAuth2 centralizado (el refresh
// token pasa a ser responsabilidad suya); ver XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2".
/**
 * Refresh token opaco (ver {@code security.RefreshTokenService}). Solo se
 * almacena {@code tokenHash} (SHA-256 en hex) — el token en claro nunca se
 * persiste, solo se devuelve una vez al emitirlo.
 *
 * <p>Sin {@code @Version}: se rota reemplazando la fila por una nueva (nunca
 * se edita el {@code tokenHash}), y el unico campo mutable
 * ({@code revocado}) solo cambia de 0 a 1, nunca al reves — no hay edicion
 * concurrente real que proteger.</p>
 */
@Entity
@Table(name = "cta_refresh_token")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "revocado", nullable = false)
    private Boolean revocado;

    /** Puesto por MySQL (DEFAULT CURRENT_TIMESTAMP(6)); no editable desde Java. */
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefreshToken other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
