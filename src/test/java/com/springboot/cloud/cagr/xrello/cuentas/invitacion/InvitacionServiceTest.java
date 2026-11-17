package com.springboot.cloud.cagr.xrello.cuentas.invitacion;

import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.springboot.cloud.cagr.commons.mensajeria.MensajeriaPort;
import com.springboot.cloud.cagr.commons.mensajeria.MensajeriaRemotaException;
import com.springboot.cloud.cagr.commons.mensajeria.dto.EnviarMensajeRemotoRequest;
import com.springboot.cloud.cagr.commons.mensajeria.dto.MensajeRemotoDto;
import com.springboot.cloud.cagr.commons.parametrizacion.ParametrizacionCompletoPort;
import com.springboot.cloud.cagr.commons.parametrizacion.dto.ParametroRemotoDto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.limite.LimiteService;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionUsoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.ProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.RolProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvitacionService.crearInvitacion — envio de correo (EMAIL_DIRIGIDO)")
class InvitacionServiceTest {

    @Mock private InvitacionRepository invitacionRepository;
    @Mock private InvitacionUsoRepository invitacionUsoRepository;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private RolProyectoRepository rolProyectoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private LimiteService limiteService;
    @Mock private ProyectoService proyectoService;
    @Mock private EntityManager entityManager;
    @Mock private MensajeriaPort mensajeriaPort;
    @Mock private ParametrizacionCompletoPort parametrizacionCompletoPort;

    private InvitacionService service;
    private Proyecto proyecto;
    private RolProyecto rol;
    private Usuario creadaPor;

    @BeforeEach
    void setUp() {
        service = new InvitacionService(invitacionRepository, invitacionUsoRepository, proyectoRepository,
                rolProyectoRepository, usuarioRepository, limiteService, proyectoService, entityManager,
                mensajeriaPort, parametrizacionCompletoPort);

        proyecto = new Proyecto();
        proyecto.setId(1L);
        proyecto.setCodigo("PRY-1");
        proyecto.setNombre("Mi Proyecto");
        proyecto.setMiembrosActuales(1);
        Usuario owner = new Usuario();
        owner.setId(10L);
        proyecto.setOwner(owner);

        rol = new RolProyecto();
        rol.setId(2L);
        rol.setCodigo("MIEMBRO");
        rol.setNombre("Miembro");

        creadaPor = new Usuario();
        creadaPor.setId(10L);
        creadaPor.setNombre("Ana Owner");

        when(proyectoRepository.findByCodigo("PRY-1")).thenReturn(Optional.of(proyecto));
        when(rolProyectoRepository.findByCodigo("MIEMBRO")).thenReturn(Optional.of(rol));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(creadaPor));
        // null = sin limite configurado (ver InvitacionService#verificarCupoDisponible) -- el default de Mockito
        // para Integer es 0, no null, y 0 SI dispara CupoProyectoLlenoException con miembrosActuales=1.
        when(limiteService.resolver(any(), any())).thenReturn(null);
    }

    private Map<String, ParametroRemotoDto> configuracionValida() {
        ParametroRemotoDto plantilla = new ParametroRemotoDto();
        plantilla.setNombre("CORREO_PROYECTOS_INVITACION");
        plantilla.setValor("Hola, {{NOMBRE_INVITADOR}} te invito a {{NOMBRE_PROYECTO}} como {{ROL}}: {{ENLACE_INVITACION}}");
        ParametroRemotoDto url = new ParametroRemotoDto();
        url.setNombre("URL_INVITACION_PROYECTOS");
        url.setValor("http://localhost:8009/pages/registro.xhtml");
        ParametroRemotoDto asunto = new ParametroRemotoDto();
        asunto.setNombre("ASUNTO_PROYECTOS_INVITACION");
        asunto.setValor("{{NOMBRE_INVITADOR}} te invitó a {{NOMBRE_PROYECTO}}");
        return Map.of(
                "CORREO_PROYECTOS_INVITACION", plantilla,
                "URL_INVITACION_PROYECTOS", url,
                "ASUNTO_PROYECTOS_INVITACION", asunto);
    }

    @Test
    @DisplayName("EMAIL_DIRIGIDO: envia el correo con la plantilla sustituida y el enlace correcto")
    void emailDirigidoEnviaCorreo() {
        when(parametrizacionCompletoPort.cargarConfiguracionCompleta()).thenReturn(configuracionValida());
        when(mensajeriaPort.enviar(any())).thenReturn(MensajeRemotoDto.builder().id(1L).build());

        InvitacionCreada creada = service.crearInvitacion(
                "PRY-1", Invitacion.TIPO_EMAIL_DIRIGIDO, "invitado@correo.com", "MIEMBRO", 7, 5, 10L);

        assertThat(creada.invitacion().getUsosMaximos()).isEqualTo(1);
        verify(mensajeriaPort).enviar(argThat((EnviarMensajeRemotoRequest r) ->
                r.getDestinatarios().contains("invitado@correo.com")
                        && r.isHtml()
                        && r.getAsunto().equals("Ana Owner te invitó a Mi Proyecto")
                        && r.getContenido().contains("Ana Owner")
                        && r.getContenido().contains("Mi Proyecto")
                        && r.getContenido().contains("Miembro")
                        && r.getContenido().contains("http://localhost:8009/pages/registro.xhtml?token=" + creada.tokenEnClaro())
                        && !r.getContenido().contains("{{")));
    }

    @Test
    @DisplayName("EMAIL_DIRIGIDO: si el envio falla, la invitacion queda creada igual (no propaga)")
    void emailDirigidoFalloDeEnvioNoRompeLaCreacion() {
        when(parametrizacionCompletoPort.cargarConfiguracionCompleta()).thenReturn(configuracionValida());
        when(mensajeriaPort.enviar(any())).thenThrow(new MensajeriaRemotaException("caido", null, null, true));

        InvitacionCreada creada = service.crearInvitacion(
                "PRY-1", Invitacion.TIPO_EMAIL_DIRIGIDO, "invitado@correo.com", "MIEMBRO", 7, 5, 10L);

        assertThat(creada).isNotNull();
        assertThat(creada.invitacion().getEstado()).isEqualTo(Invitacion.ESTADO_ACTIVA);
    }

    @Test
    @DisplayName("EMAIL_DIRIGIDO: si ms-parametrizacion no responde, tampoco rompe la creacion")
    void emailDirigidoFalloDeParametrizacionNoRompeLaCreacion() {
        when(parametrizacionCompletoPort.cargarConfiguracionCompleta())
                .thenThrow(new com.springboot.cloud.cagr.commons.parametrizacion.ParametrizacionRemotoException(
                        "caido", null));

        InvitacionCreada creada = service.crearInvitacion(
                "PRY-1", Invitacion.TIPO_EMAIL_DIRIGIDO, "invitado@correo.com", "MIEMBRO", 7, 5, 10L);

        assertThat(creada).isNotNull();
        verify(mensajeriaPort, never()).enviar(any());
    }

    @Test
    @DisplayName("ENLACE_ABIERTO: nunca llama a mensajeria ni a parametrizacion")
    void enlaceAbiertoNoEnviaNada() {
        service.crearInvitacion("PRY-1", Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5, 10L);

        verify(mensajeriaPort, never()).enviar(any());
        verify(parametrizacionCompletoPort, never()).cargarConfiguracionCompleta();
    }
}
