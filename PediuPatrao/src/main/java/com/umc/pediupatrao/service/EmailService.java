package com.umc.pediupatrao.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Envio de e-mails do sistema.
 *
 * Dois modos:
 *  - SMTP: quando spring.mail.host está configurado (ex.: smtp.gmail.com), envia o e-mail de verdade;
 *  - LOG (desenvolvimento): sem servidor configurado, o conteúdo é escrito no log da aplicação.
 *    Assim o "Esqueci a senha" pode ser testado e apresentado sem uma conta de e-mail.
 *
 * Falhas de envio não interrompem o fluxo para o usuário (a resposta da tela é sempre a mesma);
 * ficam registradas no log para o administrador.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String remetente;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.host:}") String host,
            @Value("${pediupatrao.email.remetente:}") String remetente,
            @Value("${spring.mail.username:}") String usuarioSmtp) {
        this.mailSender = mailSender;
        this.host = host == null ? "" : host.trim();
        this.remetente = remetente != null && !remetente.isBlank() ? remetente.trim() : usuarioSmtp;
    }

    public boolean isSmtpConfigurado() {
        return !host.isEmpty() && mailSender.getIfAvailable() != null;
    }

    /** Envia o link de redefinição de senha. */
    public void enviarRedefinicaoSenha(String para, String nomeUsuario, String link, long minutosValidade) {
        String assunto = "Pediu Patrão — redefinição de senha";
        String texto = """
                Olá, %s!

                Recebemos um pedido para redefinir a sua senha no sistema Pediu Patrão.
                Para criar uma nova senha, acesse o link abaixo (válido por %d minutos e só pode ser usado uma vez):

                %s

                Se não foi você que pediu, ignore este e-mail: a sua senha atual continua valendo.
                """.formatted(nomeUsuario, minutosValidade, link);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#212529">
                  <h2 style="color:#dc3545">Pediu Patrão</h2>
                  <p>Olá, <strong>%s</strong>!</p>
                  <p>Recebemos um pedido para redefinir a sua senha.</p>
                  <p style="text-align:center;margin:28px 0">
                    <a href="%s" style="background:#0d6efd;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none">
                      Criar nova senha</a>
                  </p>
                  <p style="font-size:13px;color:#6c757d">O link vale por <strong>%d minutos</strong> e só pode ser usado uma vez.<br>
                  Se não foi você que pediu, ignore este e-mail: a sua senha atual continua valendo.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(nomeUsuario), HtmlUtils.htmlEscape(link), minutosValidade);
        enviar(para, assunto, texto, html);
    }

    void enviar(String para, String assunto, String texto, String html) {
        if (!isSmtpConfigurado()) {
            log.warn("""

                    ===== E-MAIL (modo LOG: SMTP não configurado) =====
                    Para: {}
                    Assunto: {}
                    {}
                    ===================================================""", para, assunto, texto);
            return;
        }
        try {
            MimeMessage mensagem = mailSender.getObject().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            helper.setFrom(remetente);
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(texto, html); // texto simples + HTML (o leitor de e-mail escolhe)
            mailSender.getObject().send(mensagem);
            log.info("E-mail '{}' enviado para {}", assunto, para);
        } catch (MessagingException | MailException e) {
            log.error("Falha ao enviar e-mail '{}' para {}: {}", assunto, para, e.getMessage());
        }
    }
}
