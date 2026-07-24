package com.main.nexus.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Professional;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.ExperienceLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PdfService {

    private static final DeviceRgb NEXUS_PRIMARY = new DeviceRgb(107, 110, 255);
    private static final DeviceGray GRAY_LIGHT = new DeviceGray(0.9f);
    private static final DeviceGray GRAY_TEXT = new DeviceGray(0.4f);

    public byte[] generateProfessionalProfile(Professional professional,
                                               List<PreviousProject> projects,
                                               ReputationMetrics reputation) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
        Document doc = new Document(pdfDoc, PageSize.A4);
        doc.setMargins(40, 50, 40, 50);

        try {
            var fontBold = PdfFontFactory.createFont("Helvetica-Bold");
            var fontRegular = PdfFontFactory.createFont("Helvetica");
            var fontItalic = PdfFontFactory.createFont("Helvetica-Oblique");

            // ── Header ──
            doc.add(new Paragraph("NEXUS")
                    .setFont(fontBold).setFontSize(26)
                    .setFontColor(NEXUS_PRIMARY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(2));

            doc.add(new Paragraph("Perfil Profissional")
                    .setFont(fontRegular).setFontSize(11)
                    .setFontColor(GRAY_TEXT)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(4));

            SolidLine divider = new SolidLine(1);
            divider.setColor(ColorConstants.LIGHT_GRAY);

            doc.add(new LineSeparator(divider)
                    .setMarginBottom(16));

            // ── Name ──
            doc.add(new Paragraph(professional.getName())
                    .setFont(fontBold).setFontSize(18)
                    .setMarginBottom(4));

            // Level + location
            String level = professional.getExperienceLevel() != null
                    ? translateLevel(professional.getExperienceLevel()) : "Não informado";
            String location = (professional.getCity() != null ? professional.getCity() : "")
                    + (professional.getUf() != null ? " / " + professional.getUf() : "");
            if (location.isBlank()) location = "Não informado";

            doc.add(new Paragraph(level + "  ·  " + location)
                    .setFont(fontRegular).setFontSize(10)
                    .setFontColor(GRAY_TEXT)
                    .setMarginBottom(16));

            // ── Skills ──
            addSectionTitle(doc, fontBold, "Habilidades");
            List<Skill> skills = professional.getSkills();
            if (skills != null && !skills.isEmpty()) {
                String chips = skills.stream()
                        .map(Skill::getName)
                        .collect(Collectors.joining("  ·  "));
                doc.add(new Paragraph(chips)
                        .setFont(fontRegular).setFontSize(10)
                        .setMarginBottom(14));
            } else {
                addEmptyField(doc, fontRegular);
            }

            // ── Salary ──
            addSectionTitle(doc, fontBold, "Pretensão salarial");
            Double cltSalary = professional.getExpectedSalaryCLT();
            Double pjSalary = professional.getExpectedSalaryPJ();
            Double freelanceMin = professional.getFreelanceMinExpectation();
            Double freelanceMax = professional.getFreelanceMaxExpectation();

            boolean hasAnySalaryInfo = cltSalary != null || pjSalary != null
                    || freelanceMin != null || freelanceMax != null;

            if (hasAnySalaryInfo) {
                Paragraph salaryParagraph = new Paragraph().setMarginBottom(14);
                boolean first = true;
                if (cltSalary != null) {
                    salaryParagraph.add(new Text((first ? "" : "\n") + "CLT: R$ " + String.format("%.2f", cltSalary))
                            .setFont(fontRegular).setFontSize(10));
                    first = false;
                }
                if (pjSalary != null) {
                    salaryParagraph.add(new Text((first ? "" : "\n") + "PJ: R$ " + String.format("%.2f", pjSalary))
                            .setFont(fontRegular).setFontSize(10));
                    first = false;
                }
                if (freelanceMin != null || freelanceMax != null) {
                    String range = freelanceMin != null && freelanceMax != null
                            ? String.format("R$ %.2f — R$ %.2f", freelanceMin, freelanceMax)
                            : "R$ " + String.format("%.2f", freelanceMin != null ? freelanceMin : freelanceMax);
                    salaryParagraph.add(new Text((first ? "" : "\n") + "Por projeto: " + range)
                            .setFont(fontRegular).setFontSize(10));
                }
                doc.add(salaryParagraph);
            } else {
                addEmptyField(doc, fontRegular);
            }

            // ── Projects ──
            addSectionTitle(doc, fontBold, "Histórico de projetos");
            if (projects != null && !projects.isEmpty()) {
                for (PreviousProject p : projects) {
                    String year = p.getYearOfCompletion() != null
                            ? String.valueOf(p.getYearOfCompletion()) : "—";
                    String techs = p.getTechnologies() != null ? p.getTechnologies() : "";

                    Paragraph projParagraph = new Paragraph()
                            .setFont(fontRegular).setFontSize(10)
                            .setMarginBottom(6);

                    projParagraph.add(new Text(p.getTitle())
                            .setFont(fontBold).setFontSize(10));

                    projParagraph.add(new Text("  (" + year + ")")
                            .setFont(fontRegular).setFontSize(9)
                            .setFontColor(GRAY_TEXT));

                    if (!techs.isBlank()) {
                        projParagraph.add(new Text("\n" + techs)
                                .setFont(fontItalic).setFontSize(9)
                                .setFontColor(GRAY_TEXT));
                    }

                    doc.add(projParagraph);
                }
                doc.add(new Paragraph().setMarginBottom(8));
            } else {
                addEmptyField(doc, fontRegular);
            }

            // ── Reputation ──
            addSectionTitle(doc, fontBold, "Reputação");
            if (reputation != null) {
                double score = reputation.getReputationScore() != null
                        ? reputation.getReputationScore() : 0.0;
                int totalReviews = reputation.getTotalReviews() != null
                        ? reputation.getTotalReviews() : 0;

                Table repTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                        .useAllAvailableWidth()
                        .setMarginBottom(14);

                repTable.addCell(createLabelCell("Score geral", fontBold));
                repTable.addCell(createLabelCell("Total de avaliações", fontBold));
                repTable.addCell(createValueCell(String.format("%.1f", score), fontRegular));
                repTable.addCell(createValueCell(String.valueOf(totalReviews), fontRegular));

                doc.add(repTable);
            } else {
                addEmptyField(doc, fontRegular);
            }

            // ── Footer ──
            SolidLine footerLine = new SolidLine(1);
            footerLine.setColor(ColorConstants.LIGHT_GRAY);

            doc.add(new LineSeparator(footerLine)
                    .setMarginTop(16).setMarginBottom(8));

            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            doc.add(new Paragraph("Gerado via plataforma Nexus em " + dateStr)
                    .setFont(fontItalic).setFontSize(8)
                    .setFontColor(GRAY_TEXT)
                    .setTextAlignment(TextAlignment.CENTER));

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        } finally {
            doc.close();
        }

        return out.toByteArray();
    }

    private void addSectionTitle(Document doc, com.itextpdf.kernel.font.PdfFont font, String title) {
        doc.add(new Paragraph(title)
                .setFont(font).setFontSize(12)
                .setFontColor(NEXUS_PRIMARY)
                .setMarginBottom(6)
                .setMarginTop(8));
    }

    private void addEmptyField(Document doc, com.itextpdf.kernel.font.PdfFont font) {
        doc.add(new Paragraph("Não informado")
                .setFont(font).setFontSize(10)
                .setFontColor(GRAY_TEXT)
                .setMarginBottom(14));
    }

    private Cell createLabelCell(String text, com.itextpdf.kernel.font.PdfFont bold) {
        return new Cell().add(new Paragraph(text)
                        .setFont(bold).setFontSize(9).setFontColor(GRAY_TEXT))
                .setBorder(null).setPaddingBottom(2);
    }

    private Cell createValueCell(String text, com.itextpdf.kernel.font.PdfFont regular) {
        return new Cell().add(new Paragraph(text)
                        .setFont(regular).setFontSize(11))
                .setBorder(null).setPaddingTop(0).setPaddingBottom(10);
    }

    private String translateLevel(ExperienceLevel level) {
        return switch (level) {
            case INTERNSHIP -> "Estágio";
            case TRAINEE -> "Trainee";
            case JUNIOR -> "Júnior";
            case PLENO -> "Pleno";
            case SENIOR -> "Sênior";
        };
    }
}
