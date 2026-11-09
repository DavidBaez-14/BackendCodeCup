package terminus.co.edu.ufps.codecup.competicion.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.Visibilidad;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoArbitroRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.shared.security.SupabaseJwtConverter;

/**
 * Un solo lugar para decidir quién ve un torneo (spec 0029, plan §6). Lo usan las lecturas
 * públicas, las listas con sesión, inscribirse y pedir unirse.
 *
 * <ul>
 *   <li>BORRADOR: solo sus admins.</li>
 *   <li>PUBLICO: cualquiera, con o sin cuenta.</li>
 *   <li>NO_LISTADO: quien llega con el enlace, o un participante.</li>
 *   <li>PRIVADO: solo sus participantes.</li>
 * </ul>
 * Participante: admin, árbitro, delegado o miembro de un plantel del torneo.
 */
@Service
@RequiredArgsConstructor
public class VisibilidadService {

    private final TorneoRolRepository rolRepo;
    private final PartidoArbitroRepository partidoArbitroRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository torneoRepo;

    /** Quien consulta: su cuenta y su persona, si tiene sesión y registro. */
    public record Quien(UUID cuentaId, UUID personaId) {
        public static final Quien ANONIMO = new Quien(null, null);

        public boolean anonimo() {
            return cuentaId == null;
        }
    }

    /** La cuenta de la petición, si hay sesión con registro; si no, ANONIMO. */
    public static Quien actual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return Quien.ANONIMO;
        String cuenta = jwt.getClaimAsString(SupabaseJwtConverter.CLAIM_CUENTA_ID);
        String persona = jwt.getClaimAsString(SupabaseJwtConverter.CLAIM_PERSONA_ID);
        if (cuenta == null) return Quien.ANONIMO;
        return new Quien(UUID.fromString(cuenta), persona != null ? UUID.fromString(persona) : null);
    }

    @Transactional(readOnly = true)
    public boolean puedeVer(Torneo t, Quien quien, boolean viaEnlace) {
        if (t.getEstado() == EstadoTorneo.BORRADOR) {
            return esAdmin(t, quien);
        }
        Visibilidad v = t.getVisibilidad() != null ? t.getVisibilidad() : Visibilidad.PUBLICO;
        return switch (v) {
            case PUBLICO -> true;
            case NO_LISTADO -> viaEnlace || esParticipante(t, quien);
            case PRIVADO -> esParticipante(t, quien);
        };
    }

    /** Como {@link #puedeVer}, pero 404 NO_ENCONTRADO si no: no se revela que el torneo existe. */
    public void exigirVisible(Torneo t, Quien quien, boolean viaEnlace) {
        if (!puedeVer(t, quien, viaEnlace)) {
            throw new ResourceNotFoundException("Torneo no encontrado.");
        }
    }

    /**
     * Para las lecturas por id que ya eran públicas (analytics): un torneo PRIVADO solo lo ven sus
     * participantes (404 si no). Un NO_LISTADO o un borrador se siguen viendo por id, como antes: la
     * web consulta por id y un borrador no tiene estadísticas. Un id que no existe no se bloquea aquí.
     */
    @Transactional(readOnly = true)
    public void exigirVisiblePorId(UUID torneoId) {
        torneoRepo.findById(torneoId).ifPresent(t -> {
            if (t.getVisibilidad() == Visibilidad.PRIVADO && !esParticipante(t, actual())) {
                throw new ResourceNotFoundException("Torneo no encontrado.");
            }
        });
    }

    /** {@code enlaceToken} enviado por el cliente coincide con el del torneo. */
    public static boolean conEnlace(Torneo t, String enlaceToken) {
        return enlaceToken != null && !enlaceToken.isBlank() && enlaceToken.equals(t.getEnlaceToken());
    }

    @Transactional(readOnly = true)
    public boolean esParticipante(Torneo t, Quien quien) {
        if (quien == null || quien.anonimo()) return false;
        UUID torneoId = t.getId();
        if (!rolRepo.findByCuentaId(quien.cuentaId()).stream()
                .filter(r -> r.getTorneo().getId().equals(torneoId)).toList().isEmpty()) {
            return true;
        }
        if (partidoArbitroRepo.asignadoEnTorneo(quien.cuentaId(), torneoId)) return true;
        if (quien.personaId() == null) return false;
        return equipoTorneoRepo.findByTorneoIdAndDelegadoPersonaId(torneoId, quien.personaId()).isPresent()
                || jugadorEquipoRepo.existsByPersonaIdAndTorneoId(quien.personaId(), torneoId);
    }

    private boolean esAdmin(Torneo t, Quien quien) {
        return quien != null && !quien.anonimo()
                && rolRepo.existsByTorneoIdAndCuentaIdAndRol(t.getId(), quien.cuentaId(),
                        terminus.co.edu.ufps.codecup.competicion.model.RolTorneo.ADMIN);
    }

    public Optional<UUID> cuentaDe(Quien quien) {
        return Optional.ofNullable(quien).map(Quien::cuentaId);
    }
}
