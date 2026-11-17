package com.springboot.cloud.cagr.xrello.cuentas.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.domain.MiembroProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.security.UsuarioActual;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.MiembroResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Miembros de un proyecto. {@code codigo} (path, del proyecto contenedor) participa en TODO
 * {@code @PreAuthorize} de esta clase (ver XRELLO_CONTRATO.md §5 y
 * {@code security.AccesoProyecto}). {@code usuarioId} en {@code DELETE} es, a proposito, el id
 * numerico (el usuario no tiene un {@code codigo} publico propio como proyecto/invitacion).
 */
@RestController
@RequestMapping("/api/v1/cuentas/proyectos/{codigo}/miembros")
@Tag(name = "Miembros de proyecto", description = "Miembros de un proyecto y su rol.")
public class MiembroController {

    private final ProyectoService proyectoService;
    private final UsuarioActual usuarioActual;

    public MiembroController(ProyectoService proyectoService, UsuarioActual usuarioActual) {
        this.proyectoService = proyectoService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    @PreAuthorize("@accesoProyecto.esMiembro(#codigo)")
    @Operation(summary = "Lista los miembros del proyecto y su rol — cualquier miembro puede verla")
    public ResponseEntity<ApiResponse<List<MiembroResponse>>> listar(@PathVariable @P("codigo") String codigo) {
        List<MiembroProyecto> miembros = proyectoService.listarMiembros(codigo);
        return ResponseEntity.ok(ApiResponse.success(miembros.stream().map(this::aResponse).toList()));
    }

    @DeleteMapping("/{usuarioId}")
    @PreAuthorize("@accesoProyecto.esOwner(#codigo) or @accesoProyecto.tieneRol(#codigo, 'ADMIN')")
    @Operation(summary = "Quita a un miembro del proyecto — solo OWNER/ADMIN; al OWNER no se le "
            + "puede quitar la membresia por esta via (usar transferir-propiedad primero) y nadie "
            + "puede expulsarse a si mismo")
    public ResponseEntity<Void> remover(@PathVariable @P("codigo") String codigo, @PathVariable Long usuarioId) {
        Proyecto proyecto = proyectoService.obtenerPorCodigo(codigo);
        proyectoService.removerMiembro(proyecto.getId(), usuarioId, usuarioActual.getId());
        return ResponseEntity.noContent().build();
    }

    private MiembroResponse aResponse(MiembroProyecto miembro) {
        boolean esOwner = RolProyecto.ROL_OWNER.equals(miembro.getRolProyecto().getCodigo());
        return new MiembroResponse(
                miembro.getUsuario().getId(), miembro.getUsuario().getEmail(), miembro.getUsuario().getNombre(),
                miembro.getRolProyecto().getCodigo(), esOwner);
    }
}
