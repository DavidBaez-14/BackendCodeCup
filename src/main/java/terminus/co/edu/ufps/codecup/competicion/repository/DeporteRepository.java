package terminus.co.edu.ufps.codecup.competicion.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.competicion.model.Deporte;

public interface DeporteRepository extends JpaRepository<Deporte, String> {

    List<Deporte> findByActivoTrueOrderByOrdenAsc();
}
