package terminus.co.edu.ufps.codecup.identidad.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.api.Documentos;
import terminus.co.edu.ufps.codecup.identidad.dto.MeResponseDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.RegistroRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.SolicitarRolRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.SolicitudRolResponseDTO;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.CuentaRol;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Cuentas de CodeCup sobre usuarios de Supabase Auth. Supabase autentica (correo
 * confirmado o Google) y este servicio solo guarda la cuenta, la persona y los roles.
 * Nunca llama a la API de Supabase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;
    private final PersonaRepository personaRepository;
    private final RolesCuenta rolesCuenta;
    private final InvitacionService invitacionService;

    @Transactional(readOnly = true)
    public MeResponseDTO me(String sub) {
        return toMe(cuentaDe(sub));
    }

    @Transactional
    public MeResponseDTO registrar(String sub, String correoDelToken, RegistroRequestDTO req) {
        if (cuentaRepository.existsByProveedorAndProveedorSub(Cuenta.PROVEEDOR_SUPABASE, sub)) {
            throw ApiException.conflict("CUENTA_YA_REGISTRADA", "Esta cuenta ya completó su registro.");
        }
        String numero = Documentos.normalizar(req.getNumeroDocumento());
        if (numero == null) {
            throw ApiException.badRequest("DOCUMENTO_INVALIDO", "Escribe el número de documento.");
        }
        // Hasta M2 la competición identifica a las personas solo por el número:
        // CC 1090 y TI 1090 serían la misma "cédula". Por eso no se repite el número.
        if (personaRepository.existsByNumeroDocumento(numero)) {
            throw ApiException.conflict("DOCUMENTO_REGISTRADO",
                    "Ese documento ya está registrado. Si es tuyo, comunícate con un administrador.");
        }

        var persona = personaRepository.save(Persona.builder()
                .nombres(req.getNombres().trim())
                .apellidos(req.getApellidos().trim())
                .tipoDocumento(req.getTipoDocumento())
                .numeroDocumento(numero)
                .build());
        var cuenta = cuentaRepository.save(Cuenta.builder()
                .proveedor(Cuenta.PROVEEDOR_SUPABASE)
                .proveedorSub(sub)
                .correo(Documentos.normalizarCorreo(correoDelToken))
                .persona(persona)
                .estado(EstadoCuenta.ACTIVA)
                .fechaCreacion(LocalDateTime.now())
                .build());

        rolesCuenta.otorgar(cuenta, Rol.JUGADOR, "Registro");
        invitacionService.aplicarPendientes(cuenta);

        log.info("[AUDIT][CUENTA_REGISTRADA] cuenta={}", cuenta.getId());
        return toMe(cuenta);
    }

    @Transactional
    public SolicitudRolResponseDTO solicitarRol(String sub, SolicitarRolRequestDTO req) {
        var cuenta = cuentaDe(sub);
        if (req.getRol() == Rol.ADMINISTRADOR || req.getRol() == Rol.JUGADOR) {
            throw ApiException.badRequest("ROL_NO_SOLICITABLE",
                    "Solo se puede solicitar DELEGADO o ARBITRO. JUGADOR ya lo tienes desde el registro.");
        }
        if (cuentaRolRepository.existsByCuentaIdAndRol(cuenta.getId(), req.getRol())) {
            throw ApiException.conflict("SOLICITUD_EXISTENTE", "Ya tienes una solicitud o el rol " + req.getRol() + ".");
        }

        cuentaRolRepository.save(CuentaRol.builder()
                .cuenta(cuenta)
                .rol(req.getRol())
                .estado(EstadoRol.PENDIENTE)
                .fechaSolicitud(LocalDateTime.now())
                .motivoSolicitud(trimOrNull(req.getMotivoSolicitud()))
                .build());

        return SolicitudRolResponseDTO.builder()
                .estado(EstadoRol.PENDIENTE.name())
                .mensaje("Solicitud enviada. Espera aprobación del administrador.")
                .build();
    }

    @Transactional
    public MeResponseDTO aceptarInvitacion(String sub, String correoDelToken, String token) {
        var cuenta = cuentaDe(sub);
        invitacionService.aceptar(token, cuenta, correoDelToken);
        return toMe(cuenta);
    }

    private Cuenta cuentaDe(String sub) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, sub)
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_REGISTRADA",
                        "Completa tu registro en CodeCup para continuar."));
    }

    private MeResponseDTO toMe(Cuenta cuenta) {
        var persona = cuenta.getPersona();
        return MeResponseDTO.builder()
                .cuentaId(cuenta.getId())
                .personaId(persona != null ? persona.getId() : null)
                .nombres(persona != null ? persona.getNombres() : null)
                .apellidos(persona != null ? persona.getApellidos() : null)
                .nombre(persona != null ? persona.nombreCompleto() : null)
                .correo(cuenta.getCorreo())
                .tipoDocumento(persona != null && persona.getTipoDocumento() != null
                        ? persona.getTipoDocumento().name() : null)
                .numeroDocumento(persona != null ? persona.getNumeroDocumento() : null)
                .cedula(persona != null ? persona.getNumeroDocumento() : null)
                .estado(cuenta.getEstado().name())
                .roles(rolesCuenta.aprobados(cuenta))
                .rolesPendientes(rolesCuenta.pendientes(cuenta))
                .build();
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        var t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
