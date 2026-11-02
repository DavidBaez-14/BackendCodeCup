package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;

/** Puerta de entrada de la capa de seguridad al módulo de identidad. */
@Service
@RequiredArgsConstructor
public class ConsultaCuentas {

    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;

    /** Busca la cuenta del usuario de Supabase {@code sub}. Vacío si aún no se registró en CodeCup. */
    public Optional<CuentaAutenticada> porSubDeSupabase(String sub) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, sub).map(cuenta -> {
            var persona = cuenta.getPersona();
            var roles = cuentaRolRepository.findByCuentaIdAndEstado(cuenta.getId(), EstadoRol.APROBADO).stream()
                    .map(cr -> cr.getRol().name())
                    .sorted()
                    .toList();
            return new CuentaAutenticada(
                    cuenta.getId(),
                    persona != null ? persona.getId() : null,
                    persona != null ? persona.nombreCompleto() : null,
                    persona != null ? persona.getNumeroDocumento() : null,
                    cuenta.getEstado() == EstadoCuenta.BLOQUEADA,
                    roles);
        });
    }
}
