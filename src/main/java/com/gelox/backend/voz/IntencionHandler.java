package com.gelox.backend.voz;

import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;

/**
 * Contrato que implementa cada intención de voz. RF47 y RF48 comparten
 * RegistrarVentaHandler; RF50 y RF51 comparten ConsultaFinancieraHandler.
 */
public interface IntencionHandler {

    TipoIntencionVoz tipo();

    boolean requiereConfirmacion();

    VozResultado interpretar(VozContexto ctx);

    VozResultado ejecutar(VozPendiente pendiente, Usuario usuario);
}
