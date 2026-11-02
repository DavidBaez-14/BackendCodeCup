package terminus.co.edu.ufps.codecup.identidad.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.api.Documentos;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Invitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.InvitacionRepository;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Invitaciones por correo. El backend no envía el correo ni crea el usuario en
 * Supabase: devuelve un enlace que el admin comparte. La persona se registra con
 * ese correo y el rol se aplica solo.
 */
@Slf4j
@Service
public class InvitacionService {

    private static final Set<Rol> ROLES_INVITABLES = Set.of(Rol.ADMINISTRADOR, Rol.ARBITRO, Rol.DELEGADO);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitacionRepository invitacionRepository;
    private final CuentaRepository cuentaRepository;
    private final RolesCuenta rolesCuenta;
    private final String frontendUrl;

    public InvitacionService(InvitacionRepository invitacionRepository,
                             CuentaRepository cuentaRepository,
                             RolesCuenta rolesCuenta,
                             @Value("${app.frontend-urls}") List<String> frontendUrls) {
        this.invitacionRepository = invitacionRepository;
        this.cuentaRepository = cuentaRepository;
        this.rolesCuenta = rolesCuenta;
        this.frontendUrl = frontendUrls.isEmpty() ? "" : frontendUrls.get(0).trim();
    }

    @Transactional
    public InvitacionDTO crear(InvitacionRequestDTO req, UUID creadorCuentaId) {
        var creador = cuentaRepository.findById(creadorCuentaId)
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_REGISTRADA", "Cuenta no encontrada."));
        return toDTO(nueva(Documentos.normalizarCorreo(req.getCorreo()), req.getRol(), creador));
    }

    /** Invitación sin creador humano (BootstrapAdmin). */
    @Transactional
    public Invitacion crearDelSistema(String correo, Rol rol) {
        return nueva(Documentos.normalizarCorreo(correo), rol, null);
    }

    @Transactional(readOnly = true)
    public List<InvitacionDTO> listar(EstadoInvitacion estado) {
        var invitaciones = estado == null
                ? invitacionRepository.findAllByOrderByCreatedAtDesc()
                : invitacionRepository.findByEstadoOrderByCreatedAtDesc(estado);
        return invitaciones.stream().map(this::toDTO).toList();
    }

    @Transactional
    public void revocar(UUID invitacionId) {
        var inv = invitacionRepository.findById(invitacionId)
                .orElseThrow(() -> ApiException.notFound("INVITACION_NO_ENCONTRADA", "Invitación no encontrada."));
        if (inv.getEstado() == EstadoInvitacion.ACEPTADA) {
            throw ApiException.conflict("INVITACION_NO_VIGENTE", "La invitación ya fue aceptada; revoca el rol en su lugar.");
        }
        inv.setEstado(EstadoInvitacion.REVOCADA);
        inv.setFechaResolucion(LocalDateTime.now());
        invitacionRepository.save(inv);
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
        String correo = Documentos.normalizarCorreo(correoDelToken);
        if (correo == null || !correo.equals(inv.getCorreo())) {
            throw ApiException.forbidden("INVITACION_OTRO_CORREO",
                    "Esta invitación es para otro correo. Inicia sesión con el correo invitado.");
        }
        aplicar(inv, cuenta);
    }

    private void aplicar(Invitacion inv, Cuenta cuenta) {
        rolesCuenta.otorgar(cuenta, inv.getRol(), "Invitación");
        inv.setEstado(EstadoInvitacion.ACEPTADA);
        inv.setAceptadaPor(cuenta);
        inv.setFechaResolucion(LocalDateTime.now());
        invitacionRepository.save(inv);
        log.info("[AUDIT][INVITACION_ACEPTADA] invitacion={} cuenta={} rol={}", inv.getId(), cuenta.getId(), inv.getRol());
    }

    private Invitacion nueva(String correo, Rol rol, Cuenta creador) {
        if (!ROLES_INVITABLES.contains(rol)) {
            throw ApiException.badRequest("ROL_NO_INVITABLE",
                    "Solo se invita a ADMINISTRADOR, ARBITRO o DELEGADO. JUGADOR es automático al registrarse.");
        }
        if (invitacionRepository.existsByCorreoAndRolAndEstado(correo, rol, EstadoInvitacion.PENDIENTE)) {
            throw ApiException.conflict("INVITACION_DUPLICADA", "Ya hay una invitación pendiente para ese correo y rol.");
        }
        var inv = invitacionRepository.save(Invitacion.builder()
                .token(nuevoToken())
                .correo(correo)
                .rol(rol)
                .estado(EstadoInvitacion.PENDIENTE)
                .creadaPor(creador)
                .createdAt(LocalDateTime.now())
                .build());
        log.info("[AUDIT][INVITACION_CREADA] invitacion={} rol={} creador={}",
                inv.getId(), rol, creador != null ? creador.getId() : "sistema");
        return inv;
    }

    private InvitacionDTO toDTO(Invitacion inv) {
        return InvitacionDTO.builder()
                .id(inv.getId())
                .correo(inv.getCorreo())
                .rol(inv.getRol().name())
                .estado(inv.getEstado().name())
                .token(inv.getToken())
                .enlace(frontendUrl + "/invitacion/" + inv.getToken())
                .creadaPor(inv.getCreadaPor() != null ? inv.getCreadaPor().getCorreo() : null)
                .createdAt(inv.getCreatedAt())
                .fechaResolucion(inv.getFechaResolucion())
                .build();
    }

    private static String nuevoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
