package terminus.co.edu.ufps.codecup.identidad.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.api.Documentos;
import terminus.co.edu.ufps.codecup.identidad.api.InvitacionConAmbitoAceptada;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionVistaDTO;
import terminus.co.edu.ufps.codecup.identidad.model.AmbitoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Invitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.InvitacionRepository;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Invitaciones. El backend no envía el correo ni crea el usuario en Supabase: devuelve un enlace
 * que quien invita comparte. La persona se registra con ese correo y el rol se aplica solo, o
 * abre el enlace ya registrada y acepta.
 *
 * <p>Spec 0024: una invitación puede tener ámbito. Sin ámbito, otorga un rol global (panel de
 * Usuarios). Con ámbito TORNEO, el rol vale solo en ese torneo y lo aplica el módulo de la
 * competición al escuchar {@link InvitacionConAmbitoAceptada}. Una invitación con ámbito puede ir
 * sin correo ("por enlace"): la acepta la primera cuenta que la abra.
 */
@Slf4j
@Service
public class InvitacionService {

    private static final Set<Rol> ROLES_INVITABLES = Set.of(Rol.ADMINISTRADOR, Rol.ARBITRO, Rol.DELEGADO);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitacionRepository invitacionRepository;
    private final CuentaRepository cuentaRepository;
    private final RolesCuenta rolesCuenta;
    private final ApplicationEventPublisher eventos;
    private final String frontendUrl;

    public InvitacionService(InvitacionRepository invitacionRepository,
                             CuentaRepository cuentaRepository,
                             RolesCuenta rolesCuenta,
                             ApplicationEventPublisher eventos,
                             @Value("${app.frontend-urls}") List<String> frontendUrls) {
        this.invitacionRepository = invitacionRepository;
        this.cuentaRepository = cuentaRepository;
        this.rolesCuenta = rolesCuenta;
        this.eventos = eventos;
        this.frontendUrl = frontendUrls.isEmpty() ? "" : frontendUrls.get(0).trim();
    }

    /** Invitación a la plataforma (rol global), desde el panel de Usuarios. */
    @Transactional
    public InvitacionDTO crear(InvitacionRequestDTO req, UUID creadorCuentaId) {
        var creador = cuentaRepository.findById(creadorCuentaId)
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_REGISTRADA", "Cuenta no encontrada."));
        return toDTO(nueva(Documentos.normalizarCorreo(req.getCorreo()), req.getRol(), creador, null, null, null));
    }

    /**
     * Invitación con ámbito (spec 0024). Quien llama ya comprobó que el creador puede invitar en ese
     * ámbito (por ejemplo, que es admin del torneo).
     *
     * @param correo opcional: sin correo, la invitación es "por enlace"
     */
    @Transactional
    public InvitacionDTO crearConAmbito(AmbitoInvitacion ambito, UUID ambitoId, String descripcion, Rol rol,
                                        String correo, UUID creadorCuentaId) {
        if (ambito == null || ambitoId == null) {
            throw new IllegalArgumentException("Una invitación con ámbito necesita el ámbito y su id.");
        }
        var creador = cuentaRepository.findById(creadorCuentaId)
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_REGISTRADA", "Cuenta no encontrada."));
        return toDTO(nueva(Documentos.normalizarCorreo(correo), rol, creador, ambito, ambitoId, descripcion));
    }

    /** Invitación sin creador humano (BootstrapAdmin). */
    @Transactional
    public Invitacion crearDelSistema(String correo, Rol rol) {
        return nueva(Documentos.normalizarCorreo(correo), rol, null, null, null, null);
    }

    /** Las del panel de Usuarios: solo las invitaciones a la plataforma. */
    @Transactional(readOnly = true)
    public List<InvitacionDTO> listar(EstadoInvitacion estado) {
        var invitaciones = estado == null
                ? invitacionRepository.findByAmbitoIsNullOrderByCreatedAtDesc()
                : invitacionRepository.findByAmbitoIsNullAndEstadoOrderByCreatedAtDesc(estado);
        return invitaciones.stream().map(this::toDTO).toList();
    }

    /** Las pendientes de un ámbito (por ejemplo, de un torneo), las más nuevas primero. */
    @Transactional(readOnly = true)
    public List<InvitacionDTO> pendientesDe(AmbitoInvitacion ambito, UUID ambitoId) {
        return invitacionRepository
                .findByAmbitoAndAmbitoIdAndEstadoOrderByCreatedAtDesc(ambito, ambitoId, EstadoInvitacion.PENDIENTE)
                .stream().map(this::toDTO).toList();
    }

    /** Revoca una invitación a la plataforma. */
    @Transactional
    public void revocar(UUID invitacionId) {
        var inv = buscar(invitacionId);
        if (inv.getAmbito() != null) {
            throw ApiException.notFound("INVITACION_NO_ENCONTRADA", "Invitación no encontrada.");
        }
        revocar(inv);
    }

    /** Revoca una invitación de un ámbito; 404 si es de otro ámbito (no se revela que existe). */
    @Transactional
    public void revocarDe(UUID invitacionId, AmbitoInvitacion ambito, UUID ambitoId) {
        var inv = buscar(invitacionId);
        if (inv.getAmbito() != ambito || !ambitoId.equals(inv.getAmbitoId())) {
            throw ApiException.notFound("INVITACION_NO_ENCONTRADA", "Invitación no encontrada.");
        }
        revocar(inv);
    }

    /** Lo que ve quien abre el enlace, con o sin sesión (pantalla N-4). */
    @Transactional(readOnly = true)
    public InvitacionVistaDTO vistaPrevia(String token) {
        var inv = invitacionRepository.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("INVITACION_NO_ENCONTRADA", "Invitación no encontrada."));
        var creador = inv.getCreadaPor();
        return InvitacionVistaDTO.builder()
                .rol(inv.getRol().name())
                .ambito(inv.getAmbito() != null ? inv.getAmbito().name() : null)
                .ambitoId(inv.getAmbitoId())
                .ambitoDescripcion(inv.getAmbitoDescripcion())
                .estado(inv.getEstado().name())
                .invitadoPor(creador == null ? null
                        : creador.getPersona() != null ? creador.getPersona().nombreCompleto() : creador.getCorreo())
                .paraCorreo(enmascarar(inv.getCorreo()))
                .createdAt(inv.getCreatedAt())
                .build();
    }

    /** Aplica todas las invitaciones pendientes del correo de la cuenta (al registrarse). */
    @Transactional
    public void aplicarPendientes(Cuenta cuenta) {
        if (cuenta.getCorreo() == null) return;
        for (var inv : invitacionRepository.findByCorreoAndEstado(cuenta.getCorreo(), EstadoInvitacion.PENDIENTE)) {
            aplicar(inv, cuenta);
        }
    }

    /** Acepta una invitación concreta con una cuenta ya registrada. */
    @Transactional
    public void aceptar(String token, Cuenta cuenta, String correoDelToken) {
        var inv = invitacionRepository.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("INVITACION_NO_ENCONTRADA", "Invitación no encontrada."));
        if (inv.getEstado() != EstadoInvitacion.PENDIENTE) {
            throw ApiException.conflict("INVITACION_NO_VIGENTE", "Esta invitación ya fue usada o revocada.");
        }
        if (inv.getCorreo() != null) {
            String correo = Documentos.normalizarCorreo(correoDelToken);
            if (correo == null || !correo.equals(inv.getCorreo())) {
                throw ApiException.forbidden("INVITACION_OTRO_CORREO",
                        "Esta invitación es para otro correo. Inicia sesión con el correo invitado.");
            }
        }
        aplicar(inv, cuenta);
    }

    private void aplicar(Invitacion inv, Cuenta cuenta) {
        if (inv.getAmbito() == null) {
            rolesCuenta.otorgar(cuenta, inv.getRol(), "Invitación");
        } else {
            eventos.publishEvent(new InvitacionConAmbitoAceptada(
                    inv.getId(), inv.getAmbito(), inv.getAmbitoId(), inv.getRol(), cuenta.getId(),
                    cuenta.getPersona() != null ? cuenta.getPersona().getId() : null,
                    inv.getCreadaPor() != null ? inv.getCreadaPor().getId() : null,
                    null));
        }
        inv.setEstado(EstadoInvitacion.ACEPTADA);
        inv.setAceptadaPor(cuenta);
        inv.setFechaResolucion(LocalDateTime.now());
        invitacionRepository.save(inv);
        log.info("[AUDIT][INVITACION_ACEPTADA] invitacion={} cuenta={} rol={} ambito={}:{}",
                inv.getId(), cuenta.getId(), inv.getRol(), inv.getAmbito(), inv.getAmbitoId());
    }

    private Invitacion nueva(String correo, Rol rol, Cuenta creador, AmbitoInvitacion ambito, UUID ambitoId,
                             String descripcion) {
        if (!ROLES_INVITABLES.contains(rol)) {
            throw ApiException.badRequest("ROL_NO_INVITABLE",
                    "Solo se invita a ADMINISTRADOR, ARBITRO o DELEGADO. JUGADOR es automático al registrarse.");
        }
        if (ambito == null && correo == null) {
            throw ApiException.badRequest("VALIDACION", "Escribe el correo de la persona invitada.");
        }
        boolean duplicada = correo != null && (ambito == null
                ? invitacionRepository.existsByCorreoAndRolAndEstadoAndAmbitoIsNull(correo, rol, EstadoInvitacion.PENDIENTE)
                : invitacionRepository.existsByCorreoAndRolAndAmbitoAndAmbitoIdAndEstado(
                        correo, rol, ambito, ambitoId, EstadoInvitacion.PENDIENTE));
        if (duplicada) {
            throw ApiException.conflict("INVITACION_DUPLICADA", "Ya hay una invitación pendiente para ese correo y rol.");
        }
        var inv = invitacionRepository.save(Invitacion.builder()
                .token(nuevoToken())
                .correo(correo)
                .rol(rol)
                .ambito(ambito)
                .ambitoId(ambitoId)
                .ambitoDescripcion(descripcion)
                .estado(EstadoInvitacion.PENDIENTE)
                .creadaPor(creador)
                .createdAt(LocalDateTime.now())
                .build());
        log.info("[AUDIT][INVITACION_CREADA] invitacion={} rol={} ambito={}:{} creador={}",
                inv.getId(), rol, ambito, ambitoId, creador != null ? creador.getId() : "sistema");
        return inv;
    }

    private Invitacion buscar(UUID invitacionId) {
        return invitacionRepository.findById(invitacionId)
                .orElseThrow(() -> ApiException.notFound("INVITACION_NO_ENCONTRADA", "Invitación no encontrada."));
    }

    private void revocar(Invitacion inv) {
        if (inv.getEstado() == EstadoInvitacion.ACEPTADA) {
            throw ApiException.conflict("INVITACION_NO_VIGENTE", "La invitación ya fue aceptada; revoca el rol en su lugar.");
        }
        inv.setEstado(EstadoInvitacion.REVOCADA);
        inv.setFechaResolucion(LocalDateTime.now());
        invitacionRepository.save(inv);
    }

    private InvitacionDTO toDTO(Invitacion inv) {
        return InvitacionDTO.builder()
                .id(inv.getId())
                .correo(inv.getCorreo())
                .rol(inv.getRol().name())
                .ambito(inv.getAmbito() != null ? inv.getAmbito().name() : null)
                .ambitoId(inv.getAmbitoId())
                .ambitoDescripcion(inv.getAmbitoDescripcion())
                .estado(inv.getEstado().name())
                .token(inv.getToken())
                .enlace(frontendUrl + "/invitacion/" + inv.getToken())
                .creadaPor(inv.getCreadaPor() != null ? inv.getCreadaPor().getCorreo() : null)
                .createdAt(inv.getCreatedAt())
                .fechaResolucion(inv.getFechaResolucion())
                .build();
    }

    /** "ana.perez@correo.com" → "a***@correo.com". */
    static String enmascarar(String correo) {
        if (correo == null) return null;
        int arroba = correo.indexOf('@');
        if (arroba <= 0) return "***";
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }

    private static String nuevoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
