package com.gelox.backend.services;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name:GELOX}")
    private String fromName;

    /**
     * Envía un correo de recuperación de contraseña con el enlace generado por Firebase.
     *
     * @param destinatario correo del usuario
     * @param enlace       enlace de restablecimiento generado por Firebase Admin SDK
     */
    public void enviarCorreoRecuperacion(String destinatario, String enlace) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(destinatario);
            helper.setSubject("Recuperación de contraseña - GELOX");
            helper.setText(construirCuerpo(enlace), true);

            mailSender.send(message);
            log.info("Correo de recuperación enviado a {}", destinatario);
        } catch (Exception e) {
            log.error("Error al enviar correo de recuperación a {}: {}", destinatario, e.getMessage());
            throw new RuntimeException("Error al enviar correo de recuperación", e);
        }
    }

    /**
     * RF03 — Notifica al usuario que su cuenta fue creada para trabajar en GELOX.
     */
    public void enviarCorreoCuentaCreada(String destinatario, String nombre, String rol,
                                         String enlaceDefinirContrasena) {
        String boton = (enlaceDefinirContrasena == null || enlaceDefinirContrasena.isBlank()) ? "" : """
                <p style="text-align:center; margin:0 0 20px;">
                  <a href="%s" style="display:inline-block; background:#9E2016; color:#fff; padding:12px 28px;
                                      border-radius:6px; text-decoration:none; font-weight:bold; font-size:14px;">
                    Definir mi contraseña
                  </a>
                </p>
                """.formatted(escapar(enlaceDefinirContrasena));
        String cuerpo = """
                <p style="margin:0 0 12px; color:#555; line-height:1.6;">
                  Hola <strong>%s</strong>, el Gerente creó tu cuenta para trabajar en <strong>GELOX</strong>
                  con el rol <strong>%s</strong>.
                </p>
                <p style="margin:0 0 20px; color:#555; line-height:1.6;">
                  Tu usuario es <strong>%s</strong>. Ingresa con la contraseña temporal que te entregó el Gerente
                  o define una nueva con el siguiente botón (el enlace vence en 1 hora).
                </p>
                %s
                <p style="margin:0; color:#999; font-size:13px; line-height:1.5;">
                  Si el enlace venció, usa la opción <em>Olvidé mi contraseña</em> en la pantalla de inicio de sesión.
                </p>
                """.formatted(escapar(nombre), etiquetaRol(rol), escapar(destinatario), boton);
        enviar(destinatario, "Tu cuenta en GELOX fue creada", "Bienvenido a GELOX", cuerpo);
    }

    /**
     * RF03 — Notifica al usuario que su cuenta fue deshabilitada (retirado del sistema).
     */
    public void enviarCorreoCuentaDeshabilitada(String destinatario, String nombre) {
        String cuerpo = """
                <p style="margin:0 0 12px; color:#555; line-height:1.6;">
                  Hola <strong>%s</strong>, te informamos que tu cuenta en <strong>GELOX</strong>
                  fue deshabilitada por el Gerente y ya no podrás iniciar sesión en el sistema.
                </p>
                <p style="margin:0; color:#999; font-size:13px; line-height:1.5;">
                  Si crees que se trata de un error, comunícate con la administración.
                </p>
                """.formatted(escapar(nombre));
        enviar(destinatario, "Tu cuenta en GELOX fue deshabilitada", "Cuenta deshabilitada", cuerpo);
    }

    private void enviar(String destinatario, String asunto, String titulo, String contenidoHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(plantilla(titulo, contenidoHtml), true);

            mailSender.send(message);
            log.info("Correo '{}' enviado a {}", asunto, destinatario);
        } catch (Exception e) {
            log.error("Error al enviar correo '{}' a {}: {}", asunto, destinatario, e.getMessage());
            throw new RuntimeException("Error al enviar correo", e);
        }
    }

    private String plantilla(String titulo, String contenidoHtml) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; color: #333; margin: 0; padding: 0;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5; padding: 40px 0;">
                      <tr><td align="center">
                        <table width="480" cellpadding="0" cellspacing="0"
                               style="background:#fff; border-radius:10px; overflow:hidden;">
                          <tr><td align="center" style="background:#9E2016; padding: 32px 40px;">
                            <span style="font-size:28px; font-weight:900; color:#fff; letter-spacing:-1px;">GELOX</span>
                          </td></tr>
                          <tr><td style="padding: 40px;">
                            <h2 style="margin:0 0 16px; color:#1a1a1a; font-size:20px;">%s</h2>
                            %s
                          </td></tr>
                          <tr><td style="background:#f9f9f9; padding:20px 40px; border-top:1px solid #eee;">
                            <p style="margin:0; color:#bbb; font-size:12px; text-align:center;">GELOX — Gestión de pedidos</p>
                          </td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(titulo, contenidoHtml);
    }

    private String etiquetaRol(String rol) {
        if ("ENCARGADO_INVENTARIO".equals(rol)) return "Encargado de Inventarios";
        if ("ENCARGADO_VENTAS".equals(rol))     return "Encargado de Ventas";
        return escapar(rol);
    }

    private String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String construirCuerpo(String enlace) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; color: #333; margin: 0; padding: 0;">
                    <table width="100%%" cellpadding="0" cellspacing="0"
                           style="background:#f5f5f5; padding: 40px 0;">
                      <tr>
                        <td align="center">
                          <table width="480" cellpadding="0" cellspacing="0"
                                 style="background:#fff; border-radius:10px;
                                        box-shadow:0 4px 12px rgba(0,0,0,0.08);
                                        overflow:hidden;">
                            <!-- Header -->
                            <tr>
                              <td align="center"
                                  style="background:#9E2016; padding: 32px 40px;">
                                <span style="font-size:28px; font-weight:900;
                                             color:#fff; letter-spacing:-1px;">
                                  GELOX
                                </span>
                              </td>
                            </tr>
                            <!-- Body -->
                            <tr>
                              <td style="padding: 40px;">
                                <h2 style="margin:0 0 16px; color:#1a1a1a; font-size:20px;">
                                  Recuperación de contraseña
                                </h2>
                                <p style="margin:0 0 12px; color:#555; line-height:1.6;">
                                  Hemos recibido una solicitud para restablecer la contraseña
                                  de tu cuenta en <strong>GELOX</strong>.
                                </p>
                                <p style="margin:0 0 28px; color:#555; line-height:1.6;">
                                  Haz clic en el botón de abajo para crear una nueva contraseña.
                                  Este enlace estará activo durante <strong>1 hora</strong>.
                                </p>
                                <p style="text-align:center; margin:0 0 28px;">
                                  <a href="%s"
                                     style="display:inline-block; background:#9E2016;
                                            color:#fff; padding:14px 32px;
                                            border-radius:6px; text-decoration:none;
                                            font-weight:bold; font-size:15px;">
                                    Restablecer contraseña
                                  </a>
                                </p>
                                <p style="margin:0; color:#999; font-size:13px; line-height:1.5;">
                                  Si no solicitaste este cambio, puedes ignorar este mensaje.
                                  Tu contraseña seguirá siendo la misma.
                                </p>
                              </td>
                            </tr>
                            <!-- Footer -->
                            <tr>
                              <td style="background:#f9f9f9; padding:20px 40px;
                                         border-top:1px solid #eee;">
                                <p style="margin:0; color:#bbb; font-size:12px;
                                          text-align:center;">
                                  GELOX — Gestión de pedidos
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(enlace);
    }
}
