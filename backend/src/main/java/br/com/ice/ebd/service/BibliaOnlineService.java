package br.com.ice.ebd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Busca o texto bíblico de uma referência na internet, para que o e-mail da leitura diária
 * traga o texto junto — e não só a referência.
 *
 * <p>Fonte: <b>bible-api.com</b> na tradução <i>João Ferreira de Almeida</i> (domínio público),
 * sem chave de acesso. A API exige o nome do livro por extenso e capítulo:versículo, então as
 * abreviações e a notação brasileira que o professor costuma digitar ("Sl 1.1-6", "1Jo 4.7-8")
 * passam por {@link ReferenciaBiblica#normalizar} antes da consulta.
 *
 * <p>É um serviço <b>best-effort</b>: qualquer falha (rede fora, referência desconhecida,
 * timeout) devolve {@code null} e o e-mail sai apenas com a referência. Nunca lança exceção.
 */
@ApplicationScoped
public class BibliaOnlineService {

    private static final Logger LOG = Logger.getLogger(BibliaOnlineService.class);

    /** Corte de segurança: capítulos inteiros não cabem num e-mail de leitura diária. */
    private static final int MAX_VERSICULOS = 25;
    private static final int MAX_CARACTERES = 3000;
    private static final String RETICENCIAS = "[...] (siga a leitura na sua Bíblia)";

    @ConfigProperty(name = "ebd.biblia.enabled", defaultValue = "true")
    boolean habilitado;

    @ConfigProperty(name = "ebd.biblia.url", defaultValue = "https://bible-api.com")
    String baseUrl;

    @ConfigProperty(name = "ebd.biblia.traducao", defaultValue = "almeida")
    String traducao;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Texto bíblico da referência, ou {@code null} se não foi possível obter (API desligada,
     * fora do ar, ou referência que a API não reconhece).
     */
    public String buscar(String referencia) {
        if (!habilitado || referencia == null || referencia.isBlank()) {
            return null;
        }
        // Um dia pode ter mais de uma leitura ("Sl 1.1-6; Pv 3.5-6") — busca cada uma.
        String[] partes = referencia.split(";");
        StringBuilder tudo = new StringBuilder();
        for (int i = 0; i < partes.length && i < ReferenciaBiblica.MAX_REFERENCIAS; i++) {
            String parte = partes[i].trim();
            if (parte.isEmpty()) {
                continue;
            }
            String texto = buscarUma(parte);
            if (texto == null) {
                continue;
            }
            if (tudo.length() > 0) {
                tudo.append("\n\n");
            }
            if (partes.length > 1) {
                tudo.append(parte).append('\n');
            }
            tudo.append(texto);
        }
        return tudo.length() == 0 ? null : tudo.toString();
    }

    /** Consulta a API para uma única referência. Null se não deu para obter. */
    private String buscarUma(String referencia) {
        String consulta = ReferenciaBiblica.normalizar(referencia);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + URLEncoder.encode(consulta, StandardCharsets.UTF_8)
                            + "?translation=" + URLEncoder.encode(traducao, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                LOG.debugf("Bíblia online: HTTP %d para '%s'.", resp.statusCode(), consulta);
                return null;
            }
            return extrairTexto(resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOG.warnf("Bíblia online indisponível para '%s': %s", consulta, e.getMessage());
            return null;
        }
    }

    /**
     * Extrai o texto da resposta da API (um versículo por linha, prefixado pelo número).
     * Devolve {@code null} quando a API respondeu com erro ou sem versículos.
     */
    public String extrairTexto(String corpo) {
        try {
            JsonNode raiz = json.readTree(corpo);
            if (raiz.hasNonNull("error")) {
                return null;
            }
            JsonNode versos = raiz.get("verses");
            if (versos == null || !versos.isArray() || versos.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            int usados = 0;
            for (JsonNode v : versos) {
                if (usados == MAX_VERSICULOS || sb.length() > MAX_CARACTERES) {
                    sb.append('\n').append(RETICENCIAS);
                    break;
                }
                String texto = limpar(v.path("text").asText(""));
                if (texto.isEmpty()) {
                    continue;
                }
                if (usados > 0) {
                    sb.append('\n');
                }
                sb.append(v.path("verse").asInt()).append(' ').append(texto);
                usados++;
            }
            String resultado = sb.toString().trim();
            return resultado.isEmpty() ? null : resultado;
        } catch (Exception e) {
            LOG.debugf("Resposta da API bíblica ilegível: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Junta as quebras internas e os espaços repetidos que a API devolve dentro do versículo.
     * O padrão inclui o espaço não separável (U+00A0), que fecha os versículos da API e não
     * casa com {@code \s} — sem isso o texto chega ao e-mail com sobras no fim de cada linha.
     */
    private static String limpar(String texto) {
        return texto.replaceAll("[\\s\\u00a0]+", " ").trim();
    }
}
