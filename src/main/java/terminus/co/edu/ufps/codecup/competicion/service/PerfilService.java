package terminus.co.edu.ufps.codecup.competicion.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilPartidoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilResponseDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilResumenDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilTarjetasDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilTituloDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EventoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.PartidoJugador;
import terminus.co.edu.ufps.codecup.competicion.model.PerfilDeportivo;
import terminus.co.edu.ufps.codecup.competicion.model.TipoEvento;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PerfilDeportivoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TituloRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Mi perfil" del jugador: datos deportivos opcionales (spec 0020) y su historial.
 * Desde la spec 0022 se consulta por persona y el GET ya no crea filas.
 */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final PerfilDeportivoRepository perfilRepository;
    private final JugadorEquipoRepository jugadorEquipoRepository;
    private final PartidoJugadorRepository partidoJugadorRepository;
    private final EventoPartidoRepository eventoPartidoRepository;
    private final TituloRepository tituloRepository;
    private final DirectorioPersonas directorioPersonas;

    @Transactional(readOnly = true)
    public PerfilResponseDTO obtenerPerfil(UUID personaId) {
        var persona = directorioPersonas.requerir(personaId);
        var perfil = perfilRepository.findById(personaId).orElse(null);

        // Nombre a mostrar: apodo > nombre de la persona.
        String nombreMostrar = perfil != null && perfil.apodoVisible() != null
                ? perfil.apodoVisible()
                : persona.nombreCompleto();

        var equipos = jugadorEquipoRepository.findByPersonaIdOrderByFechaInicioDesc(personaId)
                .stream()
                .map(je -> PerfilEquipoDTO.builder()
                        .id(je.getEquipoTorneo().getEquipo().getId())
                        .nombre(je.getEquipoTorneo().getEquipo().getNombre())
                        .torneo(je.getTorneo() != null ? je.getTorneo().getNombre() : null)
                        .desde(je.getFechaInicio())
                        .hasta(je.getFechaFin())
                        .build())
                .toList();

        var participaciones = partidoJugadorRepository.findByPersonaIdAndJugoTrue(personaId);
        int goles = participaciones.stream()
                .map(PartidoJugador::getGoles)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        var eventos = eventoPartidoRepository.findByPersonaId(personaId);
        var tarjetasPorPartido = new HashMap<UUID, List<String>>();
        int amarillas = 0;
        int azules = 0;
        int rojas = 0;
        for (EventoPartido ev : eventos) {
            TipoEvento tipo = ev.getTipoEvento();
            if (tipo == TipoEvento.GOL) continue;
            if (tipo == TipoEvento.AMARILLA) amarillas++;
            else if (tipo == TipoEvento.AZUL) azules++;
            else if (tipo == TipoEvento.ROJA) rojas++;
            tarjetasPorPartido
                    .computeIfAbsent(ev.getPartido().getId(), k -> new ArrayList<>())
                    .add(tipo.name());
        }

        var titulos = tituloRepository.findByPersonaId(personaId)
                .stream()
                .map(t -> PerfilTituloDTO.builder()
                        .torneo(t.getTorneo().getNombre())
                        .equipo(t.getEquipoTorneo().getEquipo().getNombre())
                        .puesto(t.getPuesto().name())
                        .build())
                .toList();

        var partidos = participaciones.stream()
                .map(pj -> toPartidoDTO(pj, tarjetasPorPartido))
                .toList();

        var resumen = PerfilResumenDTO.builder()
                .partidosJugados(participaciones.size())
                .goles(goles)
                .tarjetas(PerfilTarjetasDTO.builder()
                        .amarillas(amarillas)
                        .azules(azules)
                        .rojas(rojas)
                        .build())
                .titulos(titulos.size())
                .build();

        return PerfilResponseDTO.builder()
                .personaId(personaId)
                .nombre(nombreMostrar)
                .alturaCm(perfil != null ? perfil.getAlturaCm() : null)
                .piernaHabil(perfil != null ? perfil.getPiernaHabil() : null)
                .posicion(perfil != null ? perfil.getPosicion() : null)
                .equipos(equipos)
                .resumen(resumen)
                .titulos(titulos)
                .partidos(partidos)
                .build();
    }

    private static final Set<String> PIERNAS_VALIDAS = Set.of("DERECHA", "IZQUIERDA", "AMBIDIESTRO");
    private static final Set<String> POSICIONES_VALIDAS =
            Set.of("PORTERO", "DEFENSA", "MEDIOCAMPISTA", "DELANTERO");

    /**
     * Persiste los datos deportivos del jugador desde el tab "Perfil". Los tres
     * son opcionales (spec 0020): se valida solo lo que llega y el resto queda
     * en null (el PUT reemplaza, asi se puede borrar un dato).
     */
    @Transactional
    public PerfilResponseDTO actualizarPerfilDeportivo(UUID personaId,
                                                       Integer alturaCm, String piernaHabil, String posicion) {
        if (alturaCm != null && (alturaCm < 100 || alturaCm > 250)) {
            throw new RuntimeException("Altura debe estar entre 100 y 250 cm.");
        }
        if (piernaHabil != null && !PIERNAS_VALIDAS.contains(piernaHabil)) {
            throw new RuntimeException("piernaHabil debe ser DERECHA, IZQUIERDA o AMBIDIESTRO.");
        }
        if (posicion != null && !POSICIONES_VALIDAS.contains(posicion)) {
            throw new RuntimeException("posicion debe ser PORTERO, DEFENSA, MEDIOCAMPISTA o DELANTERO.");
        }
        directorioPersonas.requerir(personaId);

        var perfil = perfilRepository.findById(personaId)
                .orElseGet(() -> PerfilDeportivo.builder().personaId(personaId).build());
        perfil.setAlturaCm(alturaCm);
        perfil.setPiernaHabil(piernaHabil);
        perfil.setPosicion(posicion);
        perfilRepository.save(perfil);

        return obtenerPerfil(personaId);
    }

    private PerfilPartidoDTO toPartidoDTO(PartidoJugador pj, Map<UUID, List<String>> tarjetasPorPartido) {
        var partido = pj.getPartido();
        var equipoTorneo = pj.getEquipoTorneo();
        var rival = Objects.equals(partido.getEquipoLocalTorneo().getId(), equipoTorneo.getId())
                ? partido.getEquipoVisitanteTorneo()
                : partido.getEquipoLocalTorneo();
        return PerfilPartidoDTO.builder()
                .id(partido.getId())
                .fecha(partido.getFecha())
                .equipo(equipoTorneo.getEquipo().getNombre())
                .rival(rival.getEquipo().getNombre())
                .goles(pj.getGoles())
                .tarjetas(tarjetasPorPartido.getOrDefault(partido.getId(), List.of()))
                .build();
    }
}
