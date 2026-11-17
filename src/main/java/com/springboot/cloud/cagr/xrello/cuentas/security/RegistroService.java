package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.CuentasException;
import com.springboot.cloud.cagr.xrello.cuentas.invitacion.InvitacionService;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.AprovisionamientoUsuarioService;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.IdentidadUsuario;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.UsuarioDirectorioPort;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.UsuarioRemoto;

// Alta de cuenta con password local RETIRADA (D28/Tesela): la identidad canonica (correo + clave)
// se crea en ms-usuario (ver UsuarioDirectorioClient), nunca en una tabla local de contraseñas. El
// consumo de invitacion pendiente NO cambio (es propio de Xrello) — solo cambio que ahora lo
// dispara el aprovisionamiento just-in-time (mismo que ya usa ResolutorIdentidad para un JWT
// externo), en vez del alta local directa.
/**
 * Alta de cuenta nueva, con consumo OPCIONAL de una invitacion pendiente en
 * la misma operacion logica de registro.
 *
 * <p><b>Sin clave, sin sesion:</b> la persona no escribe clave aqui — la cuenta nace en
 * ms-usuario con {@code activo=false} y un hash de relleno NO usable (ver
 * {@code UsuarioDirectorioClient#crear}, mismo criterio que el hash de relleno de
 * {@code AutenticacionServiceImpl} de ms-usuario para comparaciones en tiempo constante). Nadie
 * puede iniciar sesion con esa cuenta — ni siquiera quien la registro — hasta que pruebe que
 * controla el correo y fije su PRIMERA clave real en {@code AuthService#verificarCorreo}. Por eso
 * {@code registrar} ya no emite {@code TokenPair}: emitirlo aqui dejaria una sesion activa sobre
 * una cuenta sin clave usable, vaciando de sentido la verificacion.</p>
 */
@Service
public class RegistroService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioDirectorioPort directorioClient;
    private final AprovisionamientoUsuarioService aprovisionamientoUsuarioService;
    private final InvitacionService invitacionService;

    public RegistroService(
            UsuarioRepository usuarioRepository,
            UsuarioDirectorioPort directorioClient,
            AprovisionamientoUsuarioService aprovisionamientoUsuarioService,
            InvitacionService invitacionService) {
        this.usuarioRepository = usuarioRepository;
        this.directorioClient = directorioClient;
        this.aprovisionamientoUsuarioService = aprovisionamientoUsuarioService;
        this.invitacionService = invitacionService;
    }

    /**
     * Crea la identidad canonica en ms-usuario (sin clave usable, ver Javadoc de clase) y, si
     * viene {@code tokenInvitacionOpcional}, intenta consumirla — ver Javadoc de
     * {@link InvitacionService#aceptarInvitacion} para por que ({@code REQUIRES_NEW}/
     * {@code NESTED} no sirven aqui) y como se completa ({@code noRollbackFor} explicito +
     * compensacion manual, no un rollback automatico). La membresia de proyecto no depende de que
     * la cuenta ya este verificada: para cuando la persona verifique y pueda entrar, ya es
     * miembro.
     *
     * <p><b>Una invitacion invalida/agotada/con limite excedido NUNCA aborta el registro</b>: se
     * captura {@link CuentasException} y se reporta en {@code motivoInvitacionNoAplicada}, sin
     * relanzar.</p>
     *
     * <p>Sin {@code @Transactional} a proposito, mismo motivo que {@code AuthService.login}: cada
     * paso (alta en ms-usuario vía {@link UsuarioDirectorioClient}, aprovisionamiento local vía
     * {@link AprovisionamientoUsuarioService}, consumo de invitacion vía
     * {@link InvitacionService#aceptarInvitacion}) maneja su propia transaccion.</p>
     */
    public RegistroResultado registrar(String email, String nombre, String tokenInvitacionOpcional) {
        String correo = email.trim().toLowerCase(Locale.ROOT);
        UsuarioRemoto remoto = directorioClient.crear(correo, correo, nombre);

        IdentidadUsuario identidad = aprovisionamientoUsuarioService.resolverOAprovisionar(
                String.valueOf(remoto.id()), remoto.correo(), remoto.nombres(), false);
        Usuario usuario = usuarioRepository.findById(identidad.usuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario local recien creado no encontrado: id=" + identidad.usuarioId()));

        boolean invitacionAplicada = false;
        String motivo = null;
        if (tokenInvitacionOpcional != null && !tokenInvitacionOpcional.isBlank()) {
            try {
                invitacionService.aceptarInvitacion(
                        tokenInvitacionOpcional, usuario.getId(), identidad.aprovisionadoEnEstaPeticion());
                invitacionAplicada = true;
            } catch (CuentasException ex) {
                motivo = ex.getMessage();
            }
        }

        return new RegistroResultado(usuario, invitacionAplicada, motivo);
    }
}
