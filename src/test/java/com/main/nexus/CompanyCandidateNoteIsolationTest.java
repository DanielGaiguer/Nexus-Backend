package com.main.nexus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// Trava de regressão para a privacidade das notas internas (CompanyCandidateNote, Passo 2).
//
// A entidade é company-only: NENHUM código voltado ao profissional pode referenciá-la. Como o
// export LGPD (UserDataExportService) monta um Map<String,Object> dinâmico -- impossível de
// inspecionar por reflection -- a checagem é feita no CÓDIGO-FONTE: só um punhado de arquivos
// (os que criam/servem a nota internamente, + os dois comentários "pendente de revisão jurídica")
// pode citar o nome CompanyCandidateNote. Qualquer outro arquivo que o mencione FAZ este teste
// falhar de propósito.
class CompanyCandidateNoteIsolationTest {

    private static final String TOKEN = "CompanyCandidateNote";

    // Arquivos que PODEM citar CompanyCandidateNote:
    //  - a própria fundação da feature (model/repo/service/controller/dtos);
    //  - UserDataExportService e PdfService: SÓ em comentário ("omissão intencional, pendente de
    //    revisão jurídica" / "não tem acesso a essas notas"), nunca como import/tipo.
    private static final Set<String> ALLOWED = Set.of(
            "model/CompanyCandidateNote.java",
            "repository/CompanyCandidateNoteRepository.java",
            "service/CompanyCandidateNoteService.java",
            "controller/CompanyCandidateNoteController.java",
            "dto/CompanyCandidateNoteDTO.java",
            "dto/CompanyCandidateNoteRequestDTO.java",
            "dto/CompanyCandidateNoteUpdateDTO.java",
            "service/UserDataExportService.java",
            "service/PdfService.java");

    // Arquivos que servem dados ao profissional -- aqui a proibição é reforçada: nem em comentário.
    private static final List<String> PROFESSIONAL_FACING = List.of(
            "controller/MatchController.java",
            "controller/ScreeningInvitationController.java",
            "controller/ProfessionalController.java",
            "service/ScreeningInvitationService.java",
            "dto/MatchResponseDTO.java",
            "dto/ScreeningInvitationSummaryDTO.java",
            "dto/ScreeningInvitationDetailDTO.java",
            "dto/ScreeningProcessSummaryDTO.java");

    private static Path sourceRoot() {
        for (String candidate : List.of("src/main/java/com/main/nexus", "nexus/src/main/java/com/main/nexus")) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        throw new IllegalStateException(
                "Could not locate src/main/java/com/main/nexus from " + Path.of("").toAbsolutePath());
    }

    @Test
    void onlyTheFeatureItselfAndTheTwoLegalCommentsMentionTheEntity() {
        Path root = sourceRoot();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                String content = read(p);
                if (content.contains(TOKEN) && !ALLOWED.contains(rel)) {
                    offenders.add(rel);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(offenders.isEmpty(),
                "Estes arquivos citam CompanyCandidateNote fora da allow-list (risco de vazar "
                        + "nota interna para o profissional): " + offenders);
    }

    @Test
    void professionalFacingCodeNeverImportsTheEntityOrItsDtos() {
        Path root = sourceRoot();
        List<String> offenders = new ArrayList<>();
        for (String rel : PROFESSIONAL_FACING) {
            Path p = root.resolve(rel);
            if (!Files.exists(p)) {
                offenders.add(rel + " (arquivo não encontrado -- ajustar o teste)");
                continue;
            }
            read(p).lines()
                    .filter(l -> l.trim().startsWith("import "))
                    .filter(l -> l.contains(TOKEN))
                    .forEach(l -> offenders.add(rel + " -> " + l.trim()));
        }
        assertTrue(offenders.isEmpty(),
                "Código voltado ao profissional não pode importar CompanyCandidateNote*: " + offenders);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
