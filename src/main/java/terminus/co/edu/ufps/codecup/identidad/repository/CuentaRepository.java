package terminus.co.edu.ufps.codecup.identidad.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {

    /** Lo usa el converter de seguridad, que corre fuera de una transacción: trae la persona de una vez. */
    @Query("SELECT c FROM Cuenta c LEFT JOIN FETCH c.persona WHERE c.proveedor = :proveedor AND c.proveedorSub = :sub")
    Optional<Cuenta> findConPersona(@Param("proveedor") String proveedor, @Param("sub") String sub);

    boolean existsByProveedorAndProveedorSub(String proveedor, String proveedorSub);

    @Query("SELECT c FROM Cuenta c LEFT JOIN FETCH c.persona p WHERE p.numeroDocumento = :numero")
    Optional<Cuenta> findByNumeroDocumento(@Param("numero") String numeroDocumento);

    Optional<Cuenta> findByPersonaId(UUID personaId);

    List<Cuenta> findByPersonaIdIn(Collection<UUID> personaIds);

    /** Para mostrar el nombre de quien hizo una acción (aprobó, pagó, subió…): trae la persona de una vez. */
    @Query("SELECT c FROM Cuenta c LEFT JOIN FETCH c.persona WHERE c.id IN :ids")
    List<Cuenta> findConPersonaByIdIn(@Param("ids") Collection<UUID> ids);

    Optional<Cuenta> findFirstByCorreo(String correo);

    @Query(value = """
            SELECT c FROM Cuenta c LEFT JOIN c.persona p
            WHERE (:q IS NULL
                   OR LOWER(p.numeroDocumento) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(p.nombres) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(p.apellidos) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(c.correo) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (:rol IS NULL OR EXISTS (
                    SELECT 1 FROM CuentaRol cr
                    WHERE cr.cuenta = c
                      AND cr.rol = :rol
                      AND cr.estado = terminus.co.edu.ufps.codecup.identidad.model.EstadoRol.APROBADO))
            """)
    Page<Cuenta> buscar(@Param("q") String q, @Param("rol") Rol rol, Pageable pageable);
}
