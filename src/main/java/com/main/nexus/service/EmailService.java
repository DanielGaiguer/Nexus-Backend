package com.main.nexus.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${nexus.mail.from}")
    private String fromAddress;

    @Async
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    /**
     * Envia um e-mail HTML (com fallback em texto puro para clientes que nao
     * renderizam HTML). Usado pelas notificacoes com marca — ver
     * {@link EmailTemplateService}. Falha de envio nao pode derrubar o fluxo de
     * negocio (aprovar/suspender uma plataforma etc.), entao o erro so vira log.
     */
    @Async
    public void sendHtml(String to, String subject, String htmlBody, String textFallback) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textFallback, htmlBody);
            mailSender.send(message);
        } catch (MessagingException | RuntimeException e) {
            log.warn("Falha ao enviar e-mail HTML para {} (assunto: {}): {}", to, subject, e.getMessage());
        }
    }
}
