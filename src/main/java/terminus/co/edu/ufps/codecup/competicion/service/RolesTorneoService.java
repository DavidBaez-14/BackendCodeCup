package terminus.co.edu.ufps.codecup.competicion.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.RolTorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.TorneoRol;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.InvitacionConAmbitoAceptada;
import terminus.co.edu.ufps.codecup.identidad.api.Invitaciones;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionDTO;
import terminus.co.edu.ufps.codecup.identidad.model.AmbitoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

/**
 * Quién administra (o arbitra) cada torneo (spec 0024). Quien crea un torneo queda como ADMIN;
 * cualquier admin del torneo invita a otros (decisión Q2 de la 0024) y puede quitarlos, salvo al
 * último admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolesTorneoService {

    private final TorneoRepository torneoRepo;
    private final TorneoRolRepository rolRepo;
    private final DirectorioPersonas directorioPersonas;
    private final Invitaciones invitaciones;

    /** Quien crea el torneo queda como su admin. */
    @Transactional
    public void registrarCreador(Torneo torneo, UUID cuentaId) {
        otorgar(torneo, cuentaId, RolTorneo.ADMIN, cuentaId);
        log.info("[AUDIT][TORNEO_CREADO] torneo={} creador={}", torneo.getId(), cuentaId);
    }

    @Transactional(readOnly = true)
    public List<RolTorneoDTO> listar(UUID torneoId, UUID quienConsulta) {
        var roles = rolRepo.findByTorneoIdOrderByCreadoEnAsc(torneoId);
        var cuentas = directorioPersonas.cuentas(roles.stream().map(TorneoRol::getCuentaId).toList());
        return roles.stream().map(r -> {
            var c = cuentas.get(r.getCuentaId());
            return RolTorneoDTO.builder()
                    .id(r.getId())
                    .cuentaId(r.getCuentaId())
                    .personaId(c != null ? c.personaId() : null)
                    .nombre(c != null ? c.nombre() : null)
                    .correo(c != null ? c.correo() : null)
                    .rol(r.getRol().name())
                    .creadoEn(r.getCreadoEn())
                    .esTuyo(r.getCuentaId().equals(quienConsulta))
                    .build();
        }).toList();
    }

    @Transactional
    public InvitacionDTO invitar(UUID torneoId, RolTorneo rol, String correo, UUID creadorCuentaId) {
        var torneo = torneo(torneoId);
        return invitaciones.aTorneo(torneoId, torneo.getNombre(), aRolDeInvitacion(rol), correo, creadorCuentaId);
    }

    @Transactional(readOnly = true)
    public List<InvitacionDTO> invitacionesPendientes(UUID torneoId) {
        return invitaciones.pendientesDeTorneo(torneoId);
    }

    @Transactional
    public void revocarInvitacion(UUID torneoId, UUID invitacionId) {
        invitaciones.revocarDeTorneo(invitacionId, torneoId);
    }

    /** Quita un rol del torneo. Al último admin no se le puede quitar (409 ULTIMO_ADMIN_DEL_TORNEO). */
    @Transactional
    public void quitar(UUID torneoId, UUID rolId, UUID quienQuita) {
        var rol = rolRepo.findById(rolId)
                .filter(r -> r.getTorneo().getId().equals(torneoId))
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado en este torneo."));
        if (rol.getRol() == RolTorneo.ADMIN && rolRepo.countByTorneoIdAndRol(torneoId, RolTorneo.ADMIN) <= 1) {
            throw ApiException.conflict("ULTIMO_ADMIN_DEL_TORNEO",
                    "Es el último administrador del torneo. Invita a otro antes de quitarlo.");
        }
        rolRepo.delete(rol);
        log.info("[AUDIT][ROL_TORNEO_QUITADO] torneo={} cuenta={} rol={} por={}",
                torneoId, rol.getCuentaId(), rol.getRol(), quienQuita);
    }

    /**
     * Una invitación de ámbito TORNEO aceptada. Corre dentro de la transacción de la aceptación:
     * si el torneo ya no existe, la aceptación se deshace.
     */
    @EventListener
    @Transactional
    public void alAceptarInvitacion(InvitacionConAmbitoAceptada e) {
        if (e.ambito() != AmbitoInvitacion.TORNEO) return;
        var torneo = torneoRepo.findById(e.ambitoId())
                .orElseThrow(() -> ApiException.notFound("TORNEO_NO_ENCONTRADO", "El torneo de esta invitación ya no existe."));
        otorgar(torneo, e.cuentaId(), aRolDeTorneo(e.rol()), e.creadaPorCuentaId());
    }

    /** Idempotente: si ya lo tiene, no hace nada. */
    @Transactional
    public TorneoRol otorgar(Torneo torneo, UUID cuentaId, RolTorneo rol, UUID creadoPor) {
        return rolRepo.findByTorneoIdAndCuentaIdAndRol(torneo.getId(), cuentaId, rol)
                .orElseGet(() -> rolRepo.save(TorneoRol.builder()
                        .torneo(torneo)
                        .cuentaId(cuentaId)
                        .rol(rol)
                        .creadoEn(LocalDateTime.now())
                        .creadoPorCuentaId(creadoPor)
                        .build()));
    }

    private Torneo torneo(UUID torneoId) {
        return torneoRepo.findById(torneoId).orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
    }

    /** En la invitación, el co-admin de un torneo se guarda como ADMINISTRADOR con ámbito TORNEO. */
    private static Rol aRolDeInvitacion(RolTorneo rol) {
        return switch (rol) {
            case ADMIN -> Rol.ADMINISTRADOR;
        };
    }

    private static RolTorneo aRolDeTorneo(Rol rol) {
        if (rol == Rol.ADMINISTRADOR) return RolTorneo.ADMIN;
        throw ApiException.badRequest("ROL_NO_INVITABLE", "Esta invitación no tiene un rol válido para un torneo.");
    }
}
