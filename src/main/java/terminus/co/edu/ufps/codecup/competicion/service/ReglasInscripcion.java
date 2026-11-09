package terminus.co.edu.ufps.codecup.competicion.service;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.model.Deporte;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.DeporteRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Reglas para entrar a un torneo (spec 0029): inscripciones abiertas (estado y fecha de cierre),
 * cupos y tipo de competencia. Las usan la autoinscripción, el cupo por invitación y las
 * aprobaciones del admin.
 */
@Component
@RequiredArgsConstructor
public class ReglasInscripcion {

    private final EquipoTorneoRepository equipoTorneoRepo;
    private final DeporteRepository deporteRepo;
    private final Clock reloj;

    /** Estado PUBLICADO y sin pasar la fecha de cierre, en hora de Colombia. */
    public boolean abiertas(Torneo t) {
        if (t.getEstado() != EstadoTorneo.PUBLICADO) return false;
        return t.getCierreInscripciones() == null || !LocalDate.now(reloj).isAfter(t.getCierreInscripciones());
    }

    public void exigirAbiertas(Torneo t) {
        if (!abiertas(t)) {
            throw ApiException.conflict("INSCRIPCIONES_CERRADAS",
                    t.getEstado() == EstadoTorneo.PUBLICADO
                            ? "Las inscripciones de este torneo ya cerraron."
                            : "Solo se puede inscribir a torneos con inscripciones abiertas.");
        }
    }

    @Transactional(readOnly = true)
    public int aprobadas(Torneo t) {
        return equipoTorneoRepo.findByTorneoIdAndEstadoInscripcionOrderByFechaInscripcionAsc(
                t.getId(), EstadoInscripcion.APROBADO).size();
    }

    /** Hay lugar para una inscripción aprobada más. */
    public boolean hayCupo(Torneo t) {
        return t.getCupos() == null || aprobadas(t) < t.getCupos();
    }

    /** 409 CUPOS_AGOTADOS si ya están todos los cupos aprobados. */
    public void exigirCupo(Torneo t) {
        if (!hayCupo(t)) {
            throw ApiException.conflict("CUPOS_AGOTADOS",
                    "El torneo ya tiene sus " + t.getCupos() + " cupos ocupados.");
        }
    }

    /** Los torneos de deportes individuales no admiten equipos (la inscripción individual llega aparte). */
    public void exigirPorEquipos(Torneo t) {
        var deporte = deporteRepo.findById(t.getDeporte()).orElse(null);
        if (deporte != null && deporte.getTipoCompetencia() == Deporte.TipoCompetencia.INDIVIDUAL) {
            throw ApiException.conflict("TORNEO_INDIVIDUAL",
                    "Este torneo es individual: cada persona se inscribe sola, no por equipos.");
        }
    }
}
