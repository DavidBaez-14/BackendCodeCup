package terminus.co.edu.ufps.codecup.finanzas.model;

public enum EstadoMulta {
    PENDIENTE,           // Generada, sin comprobante de pago aprobado
    EN_REVISION,         // Comprobante subido, esperando aprobación del admin
    PAGADA,              // Comprobante aprobado, multa saldada
    CONDONADA            // Anulada manualmente por el admin (ej. corrección disciplinaria)
}
