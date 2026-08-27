package com.main.nexus.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Monta o corpo HTML "com a marca Nexus" das notificacoes por e-mail — cabecalho
 * com a wordmark, cor primaria da marca nos destaques/botao, rodape padronizado
 * ("Equipe Nexus"). Antes o {@link EmailService} so mandava texto puro; este
 * template e o padrao de branding reaproveitavel pedido para a feature de
 * plataforma personalizada (e disponivel para migrar os demais e-mails depois).
 *
 * CSS e inline de proposito: clientes de e-mail (Gmail, Outlook) ignoram
 * &lt;style&gt; e folhas externas.
 */
@Service
public class EmailTemplateService {

    // Espelha --nexus-primary do tema claro do frontend (globals.css).
    private static final String BRAND = "#5457e0";
    private static final String INK = "#0d1329";
    private static final String MUTED = "#5b6280";
    private static final String SURFACE = "#f7f8fc";
    private static final String BORDER = "#e2e4ee";

    @Value("${nexus.frontend.base-url}")
    private String frontendBaseUrl;

    public record Button(String label, String path) {}

    /**
     * @param heading    titulo em destaque no topo do card
     * @param paragraphs paragrafos do corpo (texto puro; ja escapado)
     * @param button     CTA opcional (o path e concatenado a nexus.frontend.base-url)
     */
    public String render(String heading, List<String> paragraphs, Button button) {
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            body.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:")
                .append(INK).append(";\">").append(escape(p)).append("</p>");
        }

        String cta = "";
        if (button != null) {
            String href = frontendBaseUrl + button.path();
            cta = "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 4px;\">"
                + "<tr><td style=\"border-radius:8px;background:" + BRAND + ";\">"
                + "<a href=\"" + escapeAttr(href) + "\" "
                + "style=\"display:inline-block;padding:12px 22px;font-size:14px;font-weight:600;"
                + "color:#ffffff;text-decoration:none;border-radius:8px;\">"
                + escape(button.label()) + "</a></td></tr></table>";
        }

        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
            + "<body style=\"margin:0;padding:0;background:" + SURFACE + ";\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"background:" + SURFACE + ";padding:24px 12px;\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"max-width:520px;background:#ffffff;border:1px solid " + BORDER + ";"
            + "border-radius:14px;overflow:hidden;\">"
            // header
            + "<tr><td style=\"padding:20px 28px;border-bottom:1px solid " + BORDER + ";\">"
            + "<span style=\"font-size:20px;font-weight:700;letter-spacing:-0.02em;color:" + INK + ";\">"
            + "nexus<span style=\"color:" + BRAND + ";\">.</span></span></td></tr>"
            // content
            + "<tr><td style=\"padding:28px;\">"
            + "<h1 style=\"margin:0 0 16px;font-size:18px;line-height:1.4;color:" + INK + ";\">"
            + escape(heading) + "</h1>"
            + body + cta
            + "</td></tr>"
            // footer
            + "<tr><td style=\"padding:18px 28px;border-top:1px solid " + BORDER + ";"
            + "font-size:12px;line-height:1.5;color:" + MUTED + ";\">"
            + "Equipe Nexus<br>Esta e uma mensagem automatica — nao e necessario responder."
            + "</td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    /** Versao texto puro do mesmo conteudo, para o fallback de sendHtml. */
    public String renderText(String heading, List<String> paragraphs, Button button) {
        StringBuilder sb = new StringBuilder();
        sb.append(heading).append("\n\n");
        for (String p : paragraphs) {
            sb.append(p).append("\n\n");
        }
        if (button != null) {
            sb.append(button.label()).append(": ").append(frontendBaseUrl).append(button.path()).append("\n\n");
        }
        sb.append("Equipe Nexus");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escape(s).replace("\"", "&quot;");
    }
}
