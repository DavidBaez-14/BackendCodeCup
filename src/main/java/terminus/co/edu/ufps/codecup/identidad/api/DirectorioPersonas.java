package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
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
                .map(p -> new PersonaResumen(
                        p.getId(),
                        p.getNumeroDocumento(),
                        p.nombreCompleto(),
                        cuentaRepository.findByPersonaId(p.getId()).map(Cuenta::getCorreo).orElse(null)));
    }
}
