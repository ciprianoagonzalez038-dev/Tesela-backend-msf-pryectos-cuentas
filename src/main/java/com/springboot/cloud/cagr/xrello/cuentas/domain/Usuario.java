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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuenta de usuario de Xrello. {@code estado} es un {@link String} plano
 * (nunca un enum Java con {@code @Enumerated}): Hibernate 6 + MySQLDialect
 * mapea {@code @Enumerated(EnumType.STRING)} a un ENUM NATIVO de MySQL, no a
 * VARCHAR — usar un enum Java aqui rompiera {@code ddl-auto=validate} contra
 * la columna {@code VARCHAR(20)} de {@code V1__esquema_inicial_cuentas.sql}
 * (verificado empiricamente al derivar ese script).
 *
 * <p>{@code @Version}: esta entidad se edita concurrentemente (perfil, estado
 * de la cuenta) — ver XRELLO_CONTRATO.md §6.</p>
 */
@Entity
@Table(name = "cta_usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_SUSPENDIDO = "SUSPENDIDO";
    public static final String ESTADO_INACTIVO = "INACTIVO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", length = 180, nullable = false, unique = true)
    private String email;

    // Ya NO se escribe (D28/Tesela, ver XRELLO_CONTRATO.md §14.0): ni login ni alta comparan/
    // escriben password local, se delega en ms-usuario. Nullable desde V4; no se borra todavia
    // porque cuentas viejas sin migrar la conservan.
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /**
     * Claim {@code sub} del futuro Authorization Server OAuth2 (ver XRELLO_CONTRATO.md, seccion
     * "MIGRACION A OAUTH2"). {@code NULL} para todo usuario creado por el registro local de hoy;
     * se completa recien con el aprovisionamiento just-in-time (ver
     * {@code security.UsuarioActual}) cuando ese usuario se autentique por primera vez contra
     * el Authorization Server.
     */
    @Column(name = "subject_externo", length = 255, unique = true)
    private String subjectExterno;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "email_verificado", nullable = false)
    private Boolean emailVerificado;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    /** Última versión del recorrido de primera vez vista u omitida; {@code null} = nunca (V11). */
    @Column(name = "onboarding_version_vista")
    private Integer onboardingVersionVista;

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
        if (!(o instanceof Usuario other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
