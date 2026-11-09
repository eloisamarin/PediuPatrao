package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

/** Montagem e envio do e-mail de redefinição (SMTP simulado) e o modo LOG sem servidor configurado. */
class EmailServiceTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provedor(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(sender);
        when(p.getObject()).thenReturn(sender);
        return p;
    }

    @Test
    void comSmtp_enviaEmailParaODestinatarioComOLink() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        EmailService servico = new EmailService(provedor(sender), "smtp.gmail.com", "", "loja@gmail.com");

        servico.enviarRedefinicaoSenha("joao@pizzaria.com", "joao", "https://site/redefinir-senha?token=ABC", 30);

        ArgumentCaptor<MimeMessage> enviada = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(enviada.capture());
        MimeMessage m = enviada.getValue();
        assertEquals("joao@pizzaria.com", m.getAllRecipients()[0].toString());
        assertEquals("loja@gmail.com", m.getFrom()[0].toString());
        assertEquals("Pediu Patrão — redefinição de senha", m.getSubject());
        ByteArrayOutputStream conteudo = new ByteArrayOutputStream();
        m.writeTo(conteudo);
        // O corpo vai codificado (quoted-printable, com quebras de linha), por isso as partes são conferidas separadas
        assertTrue(conteudo.toString().contains("redefinir-senha?token="));
        assertTrue(conteudo.toString().contains("ABC"));
        assertTrue(m.getContent() instanceof MimeMultipart); // texto simples + HTML
    }

    @Test
    void semSmtpConfigurado_naoTentaEnviar() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService servico = new EmailService(provedor(sender), "", "", "");

        assertFalse(servico.isSmtpConfigurado());
        servico.enviarRedefinicaoSenha("joao@pizzaria.com", "joao", "http://x/redefinir-senha?token=ABC", 30);
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void falhaNoSmtp_naoQuebraOFluxo() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("servidor fora"))
                .when(sender).send(any(MimeMessage.class));
        EmailService servico = new EmailService(provedor(sender), "smtp.gmail.com", "", "loja@gmail.com");

        servico.enviarRedefinicaoSenha("joao@pizzaria.com", "joao", "http://x", 30); // não lança exceção
    }
}
