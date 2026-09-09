package com.main.nexus.config;

import com.main.nexus.model.BehavioralItem;
import com.main.nexus.model.enums.BigFiveDimension;
import com.main.nexus.repository.BehavioralItemRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Semeia o banco de itens do inventário comportamental UMA ÚNICA VEZ, no bootstrap -- mesmo
// padrão de runner one-shot de LegalDocumentSeed. Idempotente pela via mais conservadora
// possível: se a tabela já tem qualquer linha, não faz nada. Nunca faz UPDATE em item existente,
// porque etapas já criadas copiaram esses textos, e mudar um item retroativamente invalidaria a
// comparação entre candidatos que responderam versões diferentes da mesma frase.
//
// FONTE: Big-Five Factor Markers (Goldberg, 1992), do International Personality Item Pool
// (ipip.ori.org/newBigFive5broadKey.htm) -- DOMÍNIO PÚBLICO, sem licença a pagar nem permissão a
// pedir. 50 itens, 10 por dimensão. Os textos abaixo são tradução pt-BR do item original, que
// fica no comentário ao lado de cada linha justamente para permitir conferência contra a fonte.
//
// DUAS EXCEÇÕES, EM OPENNESS: a escala de 10 itens do fator Intellect/Imagination traz "Have a
// rich vocabulary" e "Use difficult words". Os dois medem abertura POR VIA DE VOCABULÁRIO, o que
// num contexto de contratação vira proxy de escolaridade -- e "uso palavras difíceis" é o tipo
// de autoavaliação que um candidato responde pensando no recrutador, não em si. Foram trocados
// (decisão confirmada com o usuário) por dois itens (+) da escala de 20 itens do MESMO fator, na
// MESMA página da fonte: "Love to think up new ways of doing things" e "Love to read challenging
// material". A troca preserva o balanço da dimensão -- 7 diretos e 3 reversos, como no original.
//
// POLARIDADE DE NEUROTICISM -- LEIA ANTES DE MEXER: o IPIP publica o quinto fator chaveado como
// "Emotional Stability", que é o INVERSO de Neuroticismo. Como BigFiveDimension nomeia a
// dimensão NEUROTICISM, os dez itens daquele fator entram aqui com reverseScored INVERTIDO em
// relação à fonte -- o que na tabela do IPIP é "(-)" vira direto aqui, e vice-versa. Conferir
// esse bloco contra a fonte esperando os mesmos sinais vai dar diferença; é intencional.
//
// A ordem semeada INTERCALA as dimensões (E, A, C, N, O, E, A, C, N, O, ...) em vez de agrupar
// por fator -- agrupado, o respondente percebe o padrão e passa a responder em bloco.
//
// Este instrumento é indicativo e autodeclarado. Ver o aviso obrigatório em
// ScreeningTraitProfileDTO, que acompanha todo resultado devolvido pela API.
@Configuration
public class BehavioralItemSeed {

    private static final Logger log = LoggerFactory.getLogger(BehavioralItemSeed.class);

    // dimension + texto pt-BR + reverseScored (já na polaridade DESTE enum, ver nota acima).
    private record Item(BigFiveDimension dimension, String prompt, boolean reverseScored) {}

    @Bean
    CommandLineRunner seedBehavioralItems(BehavioralItemRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }

            List<Item> items = interleave();
            List<BehavioralItem> rows = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                Item source = items.get(i);
                BehavioralItem row = new BehavioralItem();
                row.setDimension(source.dimension());
                row.setPrompt(source.prompt());
                row.setReverseScored(source.reverseScored());
                row.setOrderIndex(i);
                row.setActive(true);
                rows.add(row);
            }
            repository.saveAll(rows);

            log.info("BehavioralItemSeed: {} itens IPIP-50 semeados (Big Five, Likert 1-5). "
                   + "Instrumento indicativo -- não é laudo psicológico.", rows.size());
        };
    }

    // Intercala as cinco listas por posição: E1, A1, C1, N1, O1, E2, A2, ...
    private List<Item> interleave() {
        List<List<Item>> byDimension = List.of(
                extraversion(), agreeableness(), conscientiousness(), neuroticism(), openness());

        List<Item> out = new ArrayList<>();
        int longest = byDimension.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < longest; i++) {
            for (List<Item> dimension : byDimension) {
                if (i < dimension.size()) {
                    out.add(dimension.get(i));
                }
            }
        }
        return out;
    }

    private List<Item> extraversion() {
        BigFiveDimension d = BigFiveDimension.EXTRAVERSION;
        return List.of(
                new Item(d, "Sou a alma da festa.", false),                                    // (+) Am the life of the party.
                new Item(d, "Falo pouco.", true),                                              // (-) Don't talk a lot.
                new Item(d, "Me sinto à vontade perto de outras pessoas.", false),             // (+) Feel comfortable around people.
                new Item(d, "Fico em segundo plano.", true),                                   // (-) Keep in the background.
                new Item(d, "Puxo conversa.", false),                                          // (+) Start conversations.
                new Item(d, "Tenho pouco a dizer.", true),                                     // (-) Have little to say.
                new Item(d, "Converso com muitas pessoas diferentes em festas.", false),       // (+) Talk to a lot of different people at parties.
                new Item(d, "Não gosto de chamar atenção para mim.", true),                    // (-) Don't like to draw attention to myself.
                new Item(d, "Não me incomodo de ser o centro das atenções.", false),           // (+) Don't mind being the center of attention.
                new Item(d, "Fico calado perto de desconhecidos.", true));                     // (-) Am quiet around strangers.
    }

    private List<Item> agreeableness() {
        BigFiveDimension d = BigFiveDimension.AGREEABLENESS;
        return List.of(
                new Item(d, "Me preocupo pouco com os outros.", true),                         // (-) Feel little concern for others.
                new Item(d, "Me interesso pelas pessoas.", false),                             // (+) Am interested in people.
                new Item(d, "Ofendo as pessoas.", true),                                       // (-) Insult people.
                new Item(d, "Me solidarizo com o que os outros sentem.", false),               // (+) Sympathize with others' feelings.
                new Item(d, "Não me interesso pelos problemas dos outros.", true),             // (-) Am not interested in other people's problems.
                new Item(d, "Tenho o coração mole.", false),                                   // (+) Have a soft heart.
                new Item(d, "Não tenho muito interesse pelos outros.", true),                  // (-) Am not really interested in others.
                new Item(d, "Arrumo tempo para os outros.", false),                            // (+) Take time out for others.
                new Item(d, "Percebo as emoções dos outros.", false),                          // (+) Feel others' emotions.
                new Item(d, "Faço as pessoas se sentirem à vontade.", false));                 // (+) Make people feel at ease.
    }

    private List<Item> conscientiousness() {
        BigFiveDimension d = BigFiveDimension.CONSCIENTIOUSNESS;
        return List.of(
                new Item(d, "Estou sempre preparado.", false),                                 // (+) Am always prepared.
                new Item(d, "Deixo minhas coisas espalhadas.", true),                          // (-) Leave my belongings around.
                new Item(d, "Presto atenção aos detalhes.", false),                            // (+) Pay attention to details.
                new Item(d, "Faço bagunça das coisas.", true),                                 // (-) Make a mess of things.
                new Item(d, "Faço minhas tarefas logo de cara.", false),                       // (+) Get chores done right away.
                new Item(d, "Costumo esquecer de colocar as coisas de volta no lugar.", true), // (-) Often forget to put things back in their proper place.
                new Item(d, "Gosto de ordem.", false),                                         // (+) Like order.
                new Item(d, "Fujo das minhas obrigações.", true),                              // (-) Shirk my duties.
                new Item(d, "Sigo uma agenda.", false),                                        // (+) Follow a schedule.
                new Item(d, "Sou rigoroso no meu trabalho.", false));                          // (+) Am exacting in my work.
    }

    // POLARIDADE INVERTIDA em relação à fonte -- ver a nota no topo da classe. O IPIP chaveia
    // este fator como Emotional Stability; aqui ele é NEUROTICISM, então cada sinal foi trocado.
    private List<Item> neuroticism() {
        BigFiveDimension d = BigFiveDimension.NEUROTICISM;
        return List.of(
                new Item(d, "Fico estressado com facilidade.", false),                         // ES(-) Get stressed out easily.
                new Item(d, "Fico relaxado na maior parte do tempo.", true),                   // ES(+) Am relaxed most of the time.
                new Item(d, "Me preocupo com as coisas.", false),                              // ES(-) Worry about things.
                new Item(d, "Raramente me sinto para baixo.", true),                           // ES(+) Seldom feel blue.
                new Item(d, "Me perturbo com facilidade.", false),                             // ES(-) Am easily disturbed.
                new Item(d, "Fico chateado com facilidade.", false),                           // ES(-) Get upset easily.
                new Item(d, "Mudo muito de humor.", false),                                    // ES(-) Change my mood a lot.
                new Item(d, "Tenho oscilações de humor frequentes.", false),                   // ES(-) Have frequent mood swings.
                new Item(d, "Me irrito com facilidade.", false),                               // ES(-) Get irritated easily.
                new Item(d, "Frequentemente me sinto para baixo.", false));                    // ES(-) Often feel blue.
    }

    private List<Item> openness() {
        BigFiveDimension d = BigFiveDimension.OPENNESS;
        return List.of(
                // Substitui "Have a rich vocabulary" -- ver a nota de exceções no topo da classe.
                new Item(d, "Adoro pensar em novas maneiras de fazer as coisas.", false),      // (+) Love to think up new ways of doing things. [escala de 20 itens]
                new Item(d, "Tenho dificuldade para entender ideias abstratas.", true),        // (-) Have difficulty understanding abstract ideas.
                new Item(d, "Tenho uma imaginação viva.", false),                              // (+) Have a vivid imagination.
                new Item(d, "Não me interesso por ideias abstratas.", true),                   // (-) Am not interested in abstract ideas.
                new Item(d, "Tenho ideias excelentes.", false),                                // (+) Have excellent ideas.
                new Item(d, "Não tenho boa imaginação.", true),                                // (-) Do not have a good imagination.
                new Item(d, "Entendo as coisas rapidamente.", false),                          // (+) Am quick to understand things.
                // Substitui "Use difficult words" -- ver a nota de exceções no topo da classe.
                new Item(d, "Gosto de ler material desafiador.", false),                       // (+) Love to read challenging material. [escala de 20 itens]
                new Item(d, "Passo tempo refletindo sobre as coisas.", false),                 // (+) Spend time reflecting on things.
                new Item(d, "Sou cheio de ideias.", false));                                   // (+) Am full of ideas.
    }
}
