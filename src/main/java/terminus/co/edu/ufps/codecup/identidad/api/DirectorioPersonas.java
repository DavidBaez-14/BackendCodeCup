package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;

/**
 * Reemplaza a Ms1JugadoresClient (HTTP al padrón de MS1). La competición pregunta
 * por una cédula y recibe la persona registrada, con una llamada en proceso.
 */
@Service
@RequiredArgsConstructor
public class DirectorioPersonas {

    private final PersonaRepository personaRepository;
    private final CuentaRepository cuentaRepository;

    @Transactional(readOnly = true)
    public Optional<PersonaResumen> buscarPorDocumento(String numeroDocumento) {
        String numero = Documentos.normalizar(numeroDocumento);
        if (numero == null) return Optional.empty();
        return personaRepository.findFirstByNumeroDocumento(numero)
                .map(p -> {
                    var cuenta = cuentaRepository.findByPersonaId(p.getId());
                    return new PersonaResumen(
                            p.getId(),
                            p.getNumeroDocumento(),
                            p.nombreCompleto(),
                            cuenta.map(c -> c.getCorreo()).orElse(null),
                            cuenta.isPresent(),
                            cuenta.map(c -> c.getEstado() == EstadoCuenta.BLOQUEADA).orElse(false));
                });
    }
}
