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
