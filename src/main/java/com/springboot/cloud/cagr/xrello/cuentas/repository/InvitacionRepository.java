package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;

public interface InvitacionRepository extends JpaRepository<Invitacion, Long> {

    Optional<Invitacion> findByTokenHash(String tokenHash);

    Optional<Invitacion> findByCodigo(String codigo);

    /**
     * Variante PAREADA de {@link #findByCodigo(String)} — exige que la invitacion pertenezca al
     * proyecto dado, no solo que exista. Usada por {@code InvitacionService#revocarInvitacion}
     * para que el {@code @PreAuthorize} del controller (que verifica el rol del caller SOBRE
     * {@code codigo}, el proyecto de la URL) realmente proteja la fila que el metodo muta — ver
     * XRELLO_CONTRATO.md, regla "Identificador protegido vs identificador mutado".
     */
    Optional<Invitacion> findByCodigoAndProyecto_Codigo(String codigo, String codigoProyecto);

    List<Invitacion> findByProyecto_Id(Long proyectoId);

    /**
     * Ocupa un uso SOLO si la invitacion sigue ACTIVA y no agoto sus usos, en una
     * unica sentencia atomica (ver {@code invitacion.InvitacionService#aceptarInvitacion}
     * — PROHIBIDO {@code SELECT} seguido de {@code UPDATE} separado). 0 filas
     * afectadas cubre AMBOS motivos (agotada o revocada) — a proposito, sin
     * distinguir cual: mismo criterio que "un motivo generico" del resto del
     * ecosistema (ver ms-onboarding, PublicCallbackController).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Invitacion i SET "
            // ORDEN IMPORTANTE: MySQL evalua las asignaciones de un UPDATE de izquierda a derecha y cada una ve las
            // columnas ya modificadas por las anteriores. «estado» va PRIMERO para comparar contra el valor ANTERIOR de
            // usos_actuales (usos_actuales + 1 >= usos_maximos); si fuera despues, ya veria el uso incrementado y
            // marcaria AGOTADA un uso antes.
            + "i.estado = CASE WHEN i.usosActuales + 1 >= i.usosMaximos THEN :estadoAgotada ELSE i.estado END, "
            + "i.usosActuales = i.usosActuales + 1 "
            + "WHERE i.id = :id AND i.usosActuales < i.usosMaximos AND i.estado = :estadoActiva")
    int ocuparUso(@Param("id") Long id, @Param("estadoActiva") String estadoActiva,
            @Param("estadoAgotada") String estadoAgotada);

    /**
     * Compensa (decrementa) un uso reclamado por {@link #ocuparUso} cuando
     * {@code CupoProyectoLlenoException} rechaza despues (ver Javadoc de
     * {@code InvitacionService#aceptarInvitacion}): esa excepcion sobrevive via
     * {@code noRollbackFor} tanto si {@code aceptarInvitacion} corre nested dentro de
     * {@code RegistroService#registrar} como si corre solo (llamada directa desde
     * {@code AceptarInvitacionController}, un usuario ya existente) — en ningun caso un rollback
     * automatico deshace este incremento. {@code usos_actuales > 0} evita dejarlo en negativo si
     * se invoca mas de una vez por error.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Invitacion i SET "
            // Si el uso que se compensa era el ULTIMO, la invitacion vuelve de AGOTADA a ACTIVA; una REVOCADA (o ya ACTIVA)
            // conserva su estado. Solo mira «estado», asi que el orden respecto a usos_actuales no importa aqui.
            + "i.estado = CASE WHEN i.estado = :estadoAgotada THEN :estadoActiva ELSE i.estado END, "
            + "i.usosActuales = i.usosActuales - 1 "
            + "WHERE i.id = :id AND i.usosActuales > 0")
    int liberarUso(@Param("id") Long id, @Param("estadoAgotada") String estadoAgotada,
            @Param("estadoActiva") String estadoActiva);
}
