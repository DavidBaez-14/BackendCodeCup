package terminus.co.edu.ufps.codecup.competicion.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilPartidoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilResponseDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilResumenDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilTarjetasDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.PerfilTituloDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EventoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.Jugador;
import terminus.co.edu.ufps.codecup.competicion.model.PartidoJugador;
import terminus.co.edu.ufps.codecup.competicion.model.TipoEvento;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TituloRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final JugadorRepository jugadorRepository;
    private final JugadorEquipoRepository jugadorEquipoRepository;
    private final PartidoJugadorRepository partidoJugadorRepository;
    private final EventoPartidoRepository eventoPartidoRepository;
    private final TituloRepository tituloRepository;
    private final DirectorioPersonas directorioPersonas;

    /**
     * @param nombre fallback si la cedula no es de una persona registrada (viene del JWT)
     */
    @Transactional
    public PerfilResponseDTO obtenerPerfil(String cedula, String nombre, String email) {
        // El Jugador local guarda datos cosmeticos/deportivos. Si no existe, lo
        // creamos con nombre/correo: de la persona registrada, del JWT como fallback
        // (la tabla exige nombre NOT NULL y varias FKs apuntan aqui).
        var jugador = jugadorRepository.findById(cedula)
                .orElseGet(() -> {
                    var persona = directorioPersonas.buscarPorDocumento(cedula).orElse(null);
                    String nombreLocal = persona != null
                            ? persona.nombreCompleto()
                            : (nombre != null && !nombre.isBlank() ? nombre : cedula);
                    String correoLocal = persona != null && persona.correo() != null
                            ? persona.correo()
                            : email;
                    return jugadorRepository.save(Jugador.builder()
                            .cedula(cedula)
                            .nombre(nombreLocal)
                            .correo(correoLocal)
                            .build());
                });

        // Nombre a mostrar: apodo > nombre de la persona registrada > nombre del JWT (fallback).
        String nombreMostrar;
        if (jugador.getApodo() != null && !jugador.getApodo().isBlank()) {
            nombreMostrar = jugador.getApodo();
        } else {
            nombreMostrar = directorioPersonas.buscarPorDocumento(cedula)
                    .map(PersonaResumen::nombreCompleto)
                    .orElse(nombre);
        }

        var equipos = jugadorEquipoRepository.findByCedulaOrderByFechaInicioDesc(cedula)
                .stream()
                .map(je -> PerfilEquipoDTO.builder()
                        .id(je.getEquipoTorneo().getEquipo().getId())
                        .nombre(je.getEquipoTorneo().getEquipo().getNombre())
                        .torneo(je.getTorneo() != null ? je.getTorneo().getNombre() : null)
                        .desde(je.getFechaInicio())
                        .hasta(je.getFechaFin())
                        .build())
                .toList();

        var participaciones = partidoJugadorRepository.findByJugadorCedulaAndJugoTrue(cedula);
        int goles = participaciones.stream()
                .map(PartidoJugador::getGoles)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        var eventos = eventoPartidoRepository.findByCedula(cedula);
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

        var titulos = tituloRepository.findByJugadorCedula(cedula)
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
                .cedula(jugador.getCedula())
                .nombre(nombreMostrar)
                .alturaCm(jugador.getAlturaCm())
                .piernaHabil(jugador.getPiernaHabil())
                .posicion(jugador.getPosicion())
                .equipos(equipos)
                .resumen(resumen)
                .titulos(titulos)
                .partidos(partidos)
                .build();
    }

    private static final java.util.Set<String> PIERNAS_VALIDAS =
            java.util.Set.of("DERECHA", "IZQUIERDA", "AMBIDIESTRO");
    private static final java.util.Set<String> POSICIONES_VALIDAS =
            java.util.Set.of("PORTERO", "DEFENSA", "MEDIOCAMPISTA", "DELANTERO");

    /**
     * Persiste los datos deportivos del jugador desde el tab "Perfil". Los tres
     * son opcionales (spec 0020): se valida solo lo que llega y el resto queda
     * en null (el PUT reemplaza, asi se puede borrar un dato).
     */
    @Transactional
    public PerfilResponseDTO actualizarPerfilDeportivo(String cedula, String nombreJwt, String emailJwt,
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

        // Reusamos la logica de obtenerPerfil para garantizar la fila local
        // (nombre/correo del padron, FKs validas, etc.).
        obtenerPerfil(cedula, nombreJwt, emailJwt);

        var jugador = jugadorRepository.findById(cedula)
                .orElseThrow(() -> new RuntimeException("Perfil no encontrado para cedula " + cedula));
        jugador.setAlturaCm(alturaCm);
        jugador.setPiernaHabil(piernaHabil);
        jugador.setPosicion(posicion);
        jugadorRepository.save(jugador);

        return obtenerPerfil(cedula, nombreJwt, emailJwt);
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
