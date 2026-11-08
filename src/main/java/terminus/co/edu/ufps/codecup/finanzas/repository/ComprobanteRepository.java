package terminus.co.edu.ufps.codecup.finanzas.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.finanzas.model.Comprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoComprobante;

public interface ComprobanteRepository extends JpaRepository<Comprobante, UUID> {

    List<Comprobante> findByEstado(EstadoComprobante estado);

    List<Comprobante> findByTipoAndEstado(TipoComprobante tipo, EstadoComprobante estado);

    List<Comprobante> findByReferenciaId(UUID referenciaId);

    List<Comprobante> findBySubidoPorCuentaIdOrderByFechaSubidaDesc(UUID subidoPorCuentaId);
}
