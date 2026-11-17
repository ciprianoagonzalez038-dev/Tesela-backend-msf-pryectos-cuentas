package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

    /**
     * Punto de entrada desde cualquier controller — los endpoints reciben el
     * {@code codigo} UUID, nunca el id numerico interno (ver XRELLO_CONTRATO.md §5).
     */
    Optional<Proyecto> findByCodigo(String codigo);

    /** Owner actual como id plano — evita cargar la entidad completa o navegar el proxy LAZY. */
    @Query(value = "SELECT owner_id FROM cta_proyecto WHERE id = :id", nativeQuery = true)
    Optional<Long> findOwnerId(@Param("id") Long id);

    /** Igual que {@link #findOwnerId(Long)} pero resuelto por {@code codigo} — evita un round-trip extra. */
    @Query(value = "SELECT owner_id FROM cta_proyecto WHERE codigo = :codigo", nativeQuery = true)
    Optional<Long> findOwnerIdByCodigo(@Param("codigo") String codigo);

    /**
     * Ocupa un cupo de miembro SOLO si {@code miembros_actuales < :limite}, en
     * una unica sentencia atomica (ver {@code proyecto.ProyectoService#agregarMiembro}
     * — PROHIBIDO {@code SELECT COUNT(*)} seguido de {@code INSERT}, ver
     * XRELLO_CONTRATO.md). 0 filas afectadas = cupo lleno.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE cta_proyecto SET miembros_actuales = miembros_actuales + 1 "
            + "WHERE id = :id AND miembros_actuales < :limite", nativeQuery = true)
    int incrementarMiembrosConLimite(@Param("id") Long id, @Param("limite") int limite);

    /** Variante sin condicion de cupo — usada cuando el limite resuelto es {@code null} (ilimitado). */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE cta_proyecto SET miembros_actuales = miembros_actuales + 1 WHERE id = :id",
            nativeQuery = true)
    int incrementarMiembrosSinLimite(@Param("id") Long id);

    /**
     * Libera un cupo (ver {@code proyecto.ProyectoService#removerMiembro}). {@code AND
     * miembros_actuales > 0} — guarda de piso, no de concurrencia: aqui no se compite por el
     * cupo (la fila de {@code cta_miembro_proyecto} ya se borro en la misma transaccion, asi que
     * el decremento SIEMPRE corresponde a un cupo realmente ocupado en el camino normal); protege
     * contra un {@code miembros_actuales} ya corrupto (ej. desincronizado antes de
     * {@code reconciliarContadorMiembros}) que sin esta condicion terminaria en negativo.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE cta_proyecto SET miembros_actuales = miembros_actuales - 1 "
            + "WHERE id = :id AND miembros_actuales > 0",
            nativeQuery = true)
    int decrementarMiembros(@Param("id") Long id);

    /** Usado por {@code reconciliarContadorMiembros} para corregir un contador desincronizado. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE cta_proyecto SET miembros_actuales = :conteo WHERE id = :id", nativeQuery = true)
    int actualizarMiembrosActuales(@Param("id") Long id, @Param("conteo") int conteo);
}
