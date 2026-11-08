package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

/**
 * Puerta de los otros módulos a las personas (spec 0022). La competición y las finanzas
 * guardan {@code persona_id} y preguntan aquí por su nombre, su documento o su cuenta, con
 * una llamada en proceso. Nunca leen los repositorios de identidad directamente.
 */
@Service
@RequiredArgsConstructor
public class DirectorioPersonas {

    private final PersonaRepository personaRepository;
    private final CuentaRepository cuentaRepository;

    @Transactional(readOnly = true)
    public Optional<PersonaResumen> buscar(UUID personaId) {
        if (personaId == null) return Optional.empty();
        return personaRepository.findById(personaId).map(p -> resumen(p, cuentaRepository.findByPersonaId(p.getId())));
    }

    /** Como {@link #buscar}, pero 404 si no existe. */
    @Transactional(readOnly = true)
    public PersonaResumen requerir(UUID personaId) {
        return buscar(personaId).orElseThrow(() -> new ResourceNotFoundException("Persona no encontrada."));
    }

    /** Varias personas en dos consultas (planteles, alineaciones): las que no existen no aparecen. */
    @Transactional(readOnly = true)
    public Map<UUID, PersonaResumen> buscarVarias(Collection<UUID> personaIds) {
        var ids = personaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<UUID, Cuenta> cuentas = cuentaRepository.findByPersonaIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getPersona().getId(), Function.identity()));
        Map<UUID, PersonaResumen> resultado = new HashMap<>();
        for (Persona p : personaRepository.findAllById(ids)) {
            resultado.put(p.getId(), resumen(p, Optional.ofNullable(cuentas.get(p.getId()))));
        }
        return resultado;
    }

    /** El número se normaliza igual que en el registro y no depende del tipo de documento. */
    @Transactional(readOnly = true)
    public Optional<PersonaResumen> buscarPorDocumento(String numeroDocumento) {
        String numero = Documentos.normalizar(numeroDocumento);
        if (numero == null) return Optional.empty();
        return personaRepository.findFirstByNumeroDocumento(numero)
                .map(p -> resumen(p, cuentaRepository.findByPersonaId(p.getId())));
    }

    /**
     * Crea la persona de un miembro que el delegado agrega y que no tiene cuenta (spec 0018).
     * El nombre va completo en {@code nombres}: el delegado lo escribe en un solo campo.
     *
     * @param tipo si llega null, CC (la 0018 no pedía el tipo)
     */
    @Transactional
    public PersonaResumen registrarSinCuenta(String nombre, TipoDocumento tipo, String numeroDocumento) {
        String numero = Documentos.normalizar(numeroDocumento);
        if (numero != null && personaRepository.existsByNumeroDocumento(numero)) {
            throw ApiException.conflict("DOCUMENTO_REGISTRADO", "Ya existe una persona con este documento.");
        }
        var persona = personaRepository.save(Persona.builder()
                .nombres(nombre.trim())
                .tipoDocumento(numero == null ? null : (tipo != null ? tipo : TipoDocumento.CC))
                .numeroDocumento(numero)
                .build());
        return resumen(persona, Optional.empty());
    }

    /** Nombres de las cuentas que hicieron algo (aprobar, pagar, subir…), para mostrarlos. */
    @Transactional(readOnly = true)
    public Map<UUID, String> nombresDeCuentas(Collection<UUID> cuentaIds) {
        List<UUID> ids = cuentaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<UUID, String> nombres = new HashMap<>();
        for (Cuenta c : cuentaRepository.findConPersonaByIdIn(ids)) {
            nombres.put(c.getId(), c.getPersona() != null ? c.getPersona().nombreCompleto() : c.getCorreo());
        }
        return nombres;
    }

    /** Nombre de una cuenta, o null si no hay cuenta (por ejemplo, una acción del seed). */
    @Transactional(readOnly = true)
    public String nombreDeCuenta(UUID cuentaId) {
        if (cuentaId == null) return null;
        return nombresDeCuentas(List.of(cuentaId)).get(cuentaId);
    }

    private static PersonaResumen resumen(Persona p, Optional<Cuenta> cuenta) {
        return new PersonaResumen(
                p.getId(),
                p.getTipoDocumento(),
                p.getNumeroDocumento(),
                p.nombreCompleto(),
                NombreVisible.de(p.getTipoDocumento(), p.getNombres(), p.getApellidos()),
                cuenta.map(Cuenta::getCorreo).orElse(null),
                cuenta.map(Cuenta::getId).orElse(null),
                cuenta.map(c -> c.getEstado() == EstadoCuenta.BLOQUEADA).orElse(false));
    }
}
