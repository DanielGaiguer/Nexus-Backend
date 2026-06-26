package com.main.nexus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeolocationService {

    private final RestTemplate restTemplate = new RestTemplate();   
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${locationiq.api.key}")
    private String locationIqApiKey;

    public record AddressData(Double latitude, Double longitude, String city, String state) {}

    public AddressData resolveFromCep(String cep) {
        String cleanCep = cep.replaceAll("[^0-9]", "");

        if (cleanCep.length() != 8) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Invalid CEP format.");
        }

        // ViaCEP — busca o endereço a partir do CEP
        String viaCepUrl = "https://viacep.com.br/ws/" + cleanCep + "/json/";
        String viaCepResponse = restTemplate.getForObject(viaCepUrl, String.class);

        JsonNode addressNode;
        try {
            addressNode = objectMapper.readTree(viaCepResponse);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502), "Failed to parse ViaCEP response.");
        }

        if (addressNode.has("erro")) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "CEP not found.");
        }

        String street = addressNode.path("logradouro").asText("");
        String city = addressNode.path("localidade").asText("");
        String state = addressNode.path("uf").asText("");

        String fullAddress = String.format("%s, %s, %s, Brazil", street, city, state);

        // LocationIQ — converte o endereço em coordenadas
        String locationIqUrl = "https://us1.locationiq.com/v1/search?key=" + locationIqApiKey
                + "&q=" + URLEncoder.encode(fullAddress, StandardCharsets.UTF_8)
                + "&format=json&limit=1&countrycodes=br";

        JsonNode geoResult;
        try {
            String geoResponse = restTemplate.getForObject(locationIqUrl, String.class);
            geoResult = objectMapper.readTree(geoResponse);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to resolve coordinates.", e);
        }

        if (!geoResult.isArray() || geoResult.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Could not geocode this address.");
        }

        double lat = geoResult.get(0).path("lat").asDouble();
        double lon = geoResult.get(0).path("lon").asDouble();

        return new AddressData(lat, lon, city, state);
    }
}