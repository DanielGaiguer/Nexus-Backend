package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regressão do duplo encoding na URI de geocodificação.
 *
 * O bug derrubava o cadastro de quem mora em rua com acento -- a maioria delas -- e passou
 * despercebido porque endereço sem acento continuava funcionando: ali o encoding duplicado só
 * atingia as vírgulas, que o geocoder ignora. Nada cobria este caminho, e por isso o teste ataca
 * a construção da URI, não a chamada de rede: assim ele é determinístico e falha na hora exata em
 * que alguém voltar a passar uma String montada à mão pro RestTemplate.
 */
class GeolocationServiceTest {

    private GeolocationService service;

    @BeforeEach
    void setUp() {
        service = new GeolocationService();
        ReflectionTestUtils.setField(service, "locationIqApiKey", "chave-de-teste");
    }

    /** O valor de `q` como o servidor do outro lado vai lê-lo, depois de um único decode. */
    private String decodedQueryParam(URI uri, String name) {
        for (String pair : uri.getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("parâmetro '" + name + "' ausente em " + uri);
    }

    @Test
    void enderecoAcentuadoSobreviveAUmUnicoDecode() {
        String endereco = "Praça da Sé, São Paulo, SP, Brazil";

        URI uri = service.geocodeUriFor(endereco);

        // O teste do bug: um decode tem que devolver o endereço ORIGINAL. Com o encoding
        // duplicado, um decode devolveria "Pra%C3%A7a da S%C3%A9, ..." -- texto que a LocationIQ
        // não casa com endereço nenhum, respondendo 404 "Unable to geocode".
        assertEquals(endereco, decodedQueryParam(uri, "q"));
    }

    @Test
    void bytesAcentuadosNaoSaoEncodadosDuasVezes() {
        URI uri = service.geocodeUriFor("Praça da Sé, São Paulo, SP, Brazil");
        String raw = uri.getRawQuery();

        assertTrue(raw.contains("%C3%A7"), "o 'ç' deve aparecer percent-encoded uma vez: " + raw);
        assertFalse(raw.contains("%25"),
                "'%25' significa que o '%' do encoding foi re-encodado -- o bug voltou: " + raw);
    }

    @Test
    void enderecoSemAcentoTambemFicaIntacto() {
        // Este caso já "funcionava" antes da correção, mas por acidente: as vírgulas iam
        // duplamente encodadas e a LocationIQ as descartava como ruído. Agora vai correto.
        String endereco = "Avenida Doutor Moraes Salles, Campinas, SP, Brazil";

        URI uri = service.geocodeUriFor(endereco);

        assertEquals(endereco, decodedQueryParam(uri, "q"));
        assertFalse(uri.getRawQuery().contains("%25"));
    }

    @Test
    void demaisParametrosDaBuscaSeguemIntactos() {
        URI uri = service.geocodeUriFor("Rua José Loureiro, Curitiba, PR, Brazil");

        assertEquals("chave-de-teste", decodedQueryParam(uri, "key"));
        assertEquals("json", decodedQueryParam(uri, "format"));
        assertEquals("1", decodedQueryParam(uri, "limit"));
        assertEquals("br", decodedQueryParam(uri, "countrycodes"));
        assertEquals("us1.locationiq.com", uri.getHost());
        assertEquals("/v1/search", uri.getPath());
    }
}
