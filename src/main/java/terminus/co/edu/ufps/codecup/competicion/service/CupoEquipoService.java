package terminus.co.edu.ufps.codecup.competicion.service;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.InvitacionConAmbitoAceptada;
import terminus.co.edu.ufps.codecup.identidad.api.Invitaciones;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionDTO;
import terminus.co.edu.ufps.codecup.identidad.model.AmbitoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

/**
 * Spec 0026: el delegado sin rol global.
 * <ul>
 *   <li>Camino (b) del plan §5.2: el admin crea el cupo de un equipo e invita al delegado. Al
 *       aceptar, se crean el equipo (de quien acepta) y la inscripción, con él en el plantel.</li>
 *   <li>El admin reasigna el delegado de una inscripción a otro miembro del plantel con cuenta.
 *       El anterior sigue en el plantel como jugador (Q3).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CupoEquipoService {

    private final TorneoRepository torneoRepo;
    private final EquipoRepository equipoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final DelegadoService delegadoService;
    private final TorneoAdminService torneoAdminService;
    private final DirectorioPersonas directorioPersonas;
    private final Invitaciones invitaciones;

    /** El cupo de un equipo en el torneo, con su enlace. Sin correo, lo toma quien abra el enlace. */
    @Transactional
    public InvitacionDTO crearCupo(UUID torneoId, String nombreEquipo, String correo, UUID creadorCuentaId) {
        var torneo = torneo(torneoId);
        if (torneo.getEstado() == EstadoTorneo.FINALIZADO) {
            throw ApiException.conflict("TORNEO_FINALIZADO", "El torneo ya terminó: no admite equipos nuevos.");
        }
        String nombre = nombreEquipo == null ? "" : nombreEquipo.trim();
        if (nombre.isEmpty() || nombre.length() > 150) {
            throw ApiException.badRequest("NOMBRE_INVALIDO", "Escribe el nombre del equipo (hasta 150 caracteres).");
        }
        return invitaciones.cupoDeEquipo(torneoId, torneo.getNombre(), nombre, correo, creadorCuentaId);
    }

    /**
     * La invitación de cupo aceptada: corre dentro de la transacción de la aceptación, así que si
     * algo falla (ya delega o juega en ese torneo, le falta el documento), no se acepta.
     */
    @EventListener
    @Transactional
    public void alAceptarInvitacion(InvitacionConAmbitoAceptada e) {
        if (e.ambito() != AmbitoInvitacion.TORNEO || e.rol() != Rol.DELEGADO) return;
        var torneo = torneo(e.ambitoId());
        UUID persona = e.personaId();
        if (torneo.getEstado() == EstadoTorneo.FINALIZADO) {
            throw ApiException.conflict("TORNEO_FINALIZADO", "El torneo ya terminó: no admite equipos nuevos.");
        }
        if (equipoTorneoRepo.findByTorneoIdAndDelegadoPersonaId(torneo.getId(), persona).isPresent()) {
            throw ApiException.conflict("YA_INSCRITO", "Ya eres delegado de un equipo en este torneo.");
        }
        if (jugadorEquipoRepo.existsByPersonaIdAndTorneoIdAndEstado(persona, torneo.getId(), EstadoMembresia.ACTIVO)) {
            throw ApiException.conflict("YA_EN_UN_EQUIPO_DEL_TORNEO",
                    "Ya juegas en un equipo de este torneo: no puedes tomar otro cupo.");
        }
        if (Boolean.TRUE.equals(torneo.getExigeDocumento())
                && directorioPersonas.requerir(persona).numeroDocumento() == null) {
            throw ApiException.conflict("DOCUMENTO_REQUERIDO",
                    "Este torneo exige documento: agrega el tuyo en tu perfil y vuelve a abrir la invitación.");
        }
        String nombre = e.datoExtra() != null && !e.datoExtra().isBlank() ? e.datoExtra().trim() : "Equipo";
        var equipo = equipoRepo.save(Equipo.builder().nombre(nombre).delegadoPersonaId(persona).build());
        // El admin ya decidió que el equipo entra: si no se cobra, queda aprobado; si se cobra,
        // espera el comprobante como cualquier inscripción.
        boolean cobra = torneo.getMontoInscripcion() != null && torneo.getMontoInscripcion().compareTo(BigDecimal.ZERO) > 0;
        var et = delegadoService.registrarInscripcion(torneo, equipo, persona,
                cobra ? EstadoInscripcion.PENDIENTE_PAGO : EstadoInscripcion.APROBADO);
        if (!cobra) {
            et.setAprobadoPorCuentaId(e.creadaPorCuentaId());
            equipoTorneoRepo.save(et);
        }
        log.info("[AUDIT][CUPO_TOMADO] torneo={} inscripcion={} delegado={}", torneo.getId(), et.getId(), persona);
    }

    /**
     * Reasigna el delegado de una inscripción. El nuevo debe estar ACTIVO en el plantel y tener
     * cuenta (409 DELEGADO_FUERA_DEL_PLANTEL o DELEGADO_SIN_CUENTA). El equipo base no cambia de dueño.
     */
    @Transactional
    public InscripcionDTO reasignarDelegado(UUID torneoId, UUID equipoTorneoId, UUID nuevaPersonaId, UUID quien) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .filter(x -> x.getTorneo().getId().equals(torneoId))
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada en este torneo."));
        if (nuevaPersonaId.equals(et.getDelegadoPersonaId())) {
            return torneoAdminService.toInscripcionDTO(et);
        }
        boolean enPlantel = jugadorEquipoRepo.findByPersonaIdAndEquipoTorneoId(nuevaPersonaId, equipoTorneoId)
                .map(je -> je.getEstado() == EstadoMembresia.ACTIVO)
                .orElse(false);
        if (!enPlantel) {
            throw ApiException.conflict("DELEGADO_FUERA_DEL_PLANTEL",
                    "El nuevo delegado debe estar activo en el plantel del equipo.");
        }
        var persona = directorioPersonas.requerir(nuevaPersonaId);
        if (!persona.tieneCuenta()) {
            throw ApiException.conflict("DELEGADO_SIN_CUENTA",
                    "El nuevo delegado necesita una cuenta para gestionar el equipo.");
        }
        if (persona.cuentaBloqueada()) {
            throw ApiException.conflict("JUGADOR_BLOQUEADO", "Esa persona tiene la cuenta bloqueada.");
        }
        UUID anterior = et.getDelegadoPersonaId();
        et.setDelegadoPersonaId(nuevaPersonaId);
        equipoTorneoRepo.save(et);
        log.info("[AUDIT][DELEGADO_REASIGNADO] inscripcion={} de={} a={} por={}", equipoTorneoId, anterior, nuevaPersonaId, quien);
        return torneoAdminService.toInscripcionDTO(et);
    }

    private Torneo torneo(UUID torneoId) {
        return torneoRepo.findById(torneoId).orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
    }
}
