package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springboot.cloud.cagr.xrello.cuentas.domain.MiembroProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoConRolView;

public interface MiembroProyectoRepository extends JpaRepository<MiembroProyecto, Long> {

    /** Conteo de proyectos ACTIVOS donde el usuario tiene exactamente el rol indicado. */
    long countByUsuario_IdAndRolProyecto_CodigoAndProyecto_Estado(Long usuarioId, String rolCodigo, String estado);

    /** Conteo de proyectos ACTIVOS donde el usuario tiene un rol DISTINTO del indicado ("como invitado"). */
    long countByUsuario_IdAndRolProyecto_CodigoNotAndProyecto_Estado(Long usuarioId, String rolCodigo, String estado);

    /** Conteo de proyectos ACTIVOS donde el usuario participa con cualquier rol (owner o invitado). */
    long countByUsuario_IdAndProyecto_Estado(Long usuarioId, String estado);

    /** Conteo real de miembros de un proyecto — usado por {@code reconciliarContadorMiembros}. */
    long countByProyecto_Id(Long proyectoId);

    Optional<MiembroProyecto> findByProyecto_IdAndUsuario_Id(Long proyectoId, Long usuarioId);

    /**
     * Igual que {@link #findByProyecto_IdAndUsuario_Id}, resuelto por el {@code codigo} UUID del
     * proyecto — usado por {@code security.AccesoProyecto} (los endpoints reciben el codigo,
     * nunca el id numerico interno, ver XRELLO_CONTRATO.md §5).
     */
    Optional<MiembroProyecto> findByProyecto_CodigoAndUsuario_Id(String codigoProyecto, Long usuarioId);

    /** Miembros de un proyecto (para {@code GET .../miembros}), resuelto por codigo. */
    List<MiembroProyecto> findByProyecto_Codigo(String codigoProyecto);

    /**
     * Igual que {@link #findByProyecto_Codigo}, con {@code usuario}/{@code rolProyecto}
     * pre-cargados en la MISMA consulta (join fetch) — el mapeo a {@code MiembroResponse} en el
     * controller navega ambas asociaciones LAZY; sin esto seria un N+1 por miembro listado.
     */
    @Query("SELECT m FROM MiembroProyecto m "
            + "JOIN FETCH m.usuario "
            + "JOIN FETCH m.rolProyecto "
            + "WHERE m.proyecto.codigo = :codigoProyecto")
    List<MiembroProyecto> findByProyecto_CodigoFetchUsuarioYRol(@Param("codigoProyecto") String codigoProyecto);

    /**
     * Proyectos ACTIVOS de un usuario junto con su rol y si es owner, en UNA sola
     * consulta (join implicito via {@code m.proyecto}/{@code m.rolProyecto}) — sin
     * N+1. Owner primero ({@code esOwner desc}), luego por nombre.
     */
    @Query("""
            SELECT new com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoConRolView(
                m.proyecto, m.rolProyecto.codigo,
                CASE WHEN m.rolProyecto.codigo = :rolOwner THEN true ELSE false END)
            FROM MiembroProyecto m
            WHERE m.usuario.id = :usuarioId
              AND m.proyecto.estado = :estadoActivo
            ORDER BY CASE WHEN m.rolProyecto.codigo = :rolOwner THEN 0 ELSE 1 END ASC, m.proyecto.nombre ASC
            """)
    List<ProyectoConRolView> findProyectosConRolByUsuarioId(
            @Param("usuarioId") Long usuarioId,
            @Param("estadoActivo") String estadoActivo,
            @Param("rolOwner") String rolOwner);
}
