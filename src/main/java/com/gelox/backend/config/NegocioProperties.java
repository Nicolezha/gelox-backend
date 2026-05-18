package com.gelox.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "negocio")
public class NegocioProperties {

    private String direccion;
    private String barrio;
    private String ciudad;
    private String horario;
    private Whatsapp whatsapp = new Whatsapp();
    private Maps maps = new Maps();

    public static class Whatsapp {
        private String numero;
        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }
    }

    public static class Maps {
        private String url;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getBarrio() { return barrio; }
    public void setBarrio(String barrio) { this.barrio = barrio; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getHorario() { return horario; }
    public void setHorario(String horario) { this.horario = horario; }

    public Whatsapp getWhatsapp() { return whatsapp; }
    public void setWhatsapp(Whatsapp whatsapp) { this.whatsapp = whatsapp; }

    public Maps getMaps() { return maps; }
    public void setMaps(Maps maps) { this.maps = maps; }
}
