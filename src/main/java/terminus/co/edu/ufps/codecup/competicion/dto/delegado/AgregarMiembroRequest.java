package terminus.co.edu.ufps.codecup.competicion.dto.delegado;


import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

/**
 * Agregar un miembro al plantel (spec 0018). La persona puede no tener cuenta:
 * en ese caso {@code nombre} es obligatorio. Si el documento es de alguien
 * registrado, se usa su nombre y el enviado se ignora.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgregarMiembroRequest {

    /**
     * Número de documento: opcional (spec 0023), salvo que el torneo lo exija. Se normaliza igual
     * que en el registro ("1.090 123" = "1090123").
     */
    private String numeroDocumento;

    /** Tipo de documento; si no llega, CC (spec 0022). Solo se usa si la persona es nueva. */
    private TipoDocumento tipoDocumento;

    /** Nombre completo. Obligatorio solo si la persona no está registrada ni jugó antes. */
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres.")
    private String nombre;

    private Integer numeroCamiseta;
}
