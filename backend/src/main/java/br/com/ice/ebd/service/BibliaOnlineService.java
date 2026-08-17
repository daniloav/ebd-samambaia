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
import java.text.Normalizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Busca o texto bíblico de uma referência na internet, para que o e-mail da leitura diária
 * traga o texto junto — e não só a referência.
 *
 * <p>Fonte: <b>bible-api.com</b> na tradução <i>João Ferreira de Almeida</i> (domínio público),
 * sem chave de acesso. A API exige o nome do livro por extenso e capítulo:versículo, então as
 * abreviações e a notação brasileira que o professor costuma digitar ("Sl 1.1-6", "1Jo 4.7-8")
 * são normalizadas antes da consulta.
 *
 * <p>É um serviço <b>best-effort</b>: qualquer falha (rede fora, referência desconhecida,
 * timeout) devolve {@code null} e o e-mail sai apenas com a referência. Nunca lança exceção.
 */
@ApplicationScoped
public class BibliaOnlineService {

    private static final Logger LOG = Logger.getLogger(BibliaOnlineService.class);

    /** Corte de segurança: capítulos inteiros não cabem num e-mail de leitura diária. */
    private static final int MAX_REFERENCIAS = 3;
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
        for (int i = 0; i < partes.length && i < MAX_REFERENCIAS; i++) {
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
        String consulta = normalizar(referencia);
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

    /**
     * Normaliza a referência para o formato que a API entende: livro por extenso e
     * {@code capítulo:versículo}. "Sl 1.1-6" vira "Salmos 1:1-6"; "1Jo 4.7" vira "1 João 4:7".
     * Referência que já vem por extenso passa quase intacta.
     */
    public static String normalizar(String referencia) {
        String ref = referencia.trim().replaceAll("\\s+", " ");
        // 1) notação brasileira de capítulo/versículo: "1.1-6" -> "1:1-6"
        ref = ref.replaceAll("(?<=\\d)\\s*[.,]\\s*(?=\\d)", ":");
        // 2) livro numerado colado na abreviação: "1Jo" -> "1 Jo"
        ref = ref.replaceAll("^(?i)([123])\\s*(?=\\p{L})", "$1 ");
        // 3) abreviação -> nome por extenso
        int corte = ref.lastIndexOf(' ');
        if (corte <= 0) {
            return ref;
        }
        String livro = ref.substring(0, corte);
        String resto = ref.substring(corte + 1);
        String porExtenso = LIVROS_ACENTUADOS.get(chaveComAcento(livro));
        if (porExtenso == null) {
            porExtenso = LIVROS.get(chave(livro));
        }
        return (porExtenso != null ? porExtenso : livro) + " " + resto;
    }

    /** Chave preservando acento — só ela distingue "Jó" de "Jo" (João). */
    private static String chaveComAcento(String livro) {
        return livro.toLowerCase().replaceAll("[^\\p{L}0-9]", "");
    }

    /** Chave de busca do livro: minúsculo, sem acento e sem pontuação ("1 Jo." -> "1jo"). */
    private static String chave(String livro) {
        String semAcento = Normalizer.normalize(livro, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Abreviações usuais (e o próprio nome sem acento) para o nome que a API reconhece.
     * Cobre os 66 livros; o que não estiver aqui vai como o professor digitou.
     */
    private static final Map<String, String> LIVROS = new LinkedHashMap<>();

    /** Mesmos livros, com o acento preservado na chave (consultado antes do mapa sem acento). */
    private static final Map<String, String> LIVROS_ACENTUADOS = new LinkedHashMap<>();

    private static void livro(String nome, String... abreviacoes) {
        LIVROS.put(chave(nome), nome);
        LIVROS_ACENTUADOS.put(chaveComAcento(nome), nome);
        for (String a : abreviacoes) {
            LIVROS.put(chave(a), nome);
            LIVROS_ACENTUADOS.put(chaveComAcento(a), nome);
        }
    }

    static {
        livro("Gênesis", "gn", "gen");
        livro("Êxodo", "ex", "exo");
        livro("Levítico", "lv", "lev");
        livro("Números", "nm", "num");
        livro("Deuteronômio", "dt", "deut");
        livro("Josué", "js", "jos");
        livro("Juízes", "jz", "juz");
        livro("Rute", "rt");
        livro("1 Samuel", "1sm", "1sa");
        livro("2 Samuel", "2sm", "2sa");
        livro("1 Reis", "1rs", "1re");
        livro("2 Reis", "2rs", "2re");
        livro("1 Crônicas", "1cr", "1cro");
        livro("2 Crônicas", "2cr", "2cro");
        livro("Esdras", "ed", "esd");
        livro("Neemias", "ne", "nee");
        livro("Ester", "et", "est");
        livro("Jó", "job");
        livro("Salmos", "sl", "sal", "salmo");
        livro("Provérbios", "pv", "prov", "pr");
        livro("Eclesiastes", "ec", "ecl");
        livro("Cânticos", "ct", "cant", "cantares", "cantico de salomao", "canticos dos canticos");
        livro("Isaías", "is", "isa");
        livro("Jeremias", "jr", "jer");
        livro("Lamentações", "lm", "lam");
        livro("Ezequiel", "ez", "eze");
        livro("Daniel", "dn", "dan");
        livro("Oséias", "os", "ose");
        livro("Joel", "jl");
        livro("Amós", "am");
        livro("Obadias", "ob");
        livro("Jonas", "jn", "jon");
        livro("Miquéias", "mq", "miq");
        livro("Naum", "na");
        livro("Habacuque", "hc", "hab");
        livro("Sofonias", "sf", "sof");
        livro("Ageu", "ag");
        livro("Zacarias", "zc", "zac");
        livro("Malaquias", "ml", "mal");
        livro("Mateus", "mt", "mat");
        livro("Marcos", "mc", "mar");
        livro("Lucas", "lc", "luc");
        livro("João", "jo", "joao");
        livro("Atos", "at", "atos dos apostolos");
        livro("Romanos", "rm", "rom");
        livro("1 Coríntios", "1co", "1cor");
        livro("2 Coríntios", "2co", "2cor");
        livro("Gálatas", "gl", "gal");
        livro("Efésios", "ef", "efe");
        livro("Filipenses", "fp", "fil");
        livro("Colossenses", "cl", "col");
        livro("1 Tessalonicenses", "1ts", "1tes");
        livro("2 Tessalonicenses", "2ts", "2tes");
        livro("1 Timóteo", "1tm", "1tim");
        livro("2 Timóteo", "2tm", "2tim");
        livro("Tito", "tt", "tit");
        livro("Filemom", "fm", "flm", "filemon");
        livro("Hebreus", "hb", "heb");
        livro("Tiago", "tg", "tia");
        livro("1 Pedro", "1pe", "1pd");
        livro("2 Pedro", "2pe", "2pd");
        livro("1 João", "1jo", "1joao");
        livro("2 João", "2jo", "2joao");
        livro("3 João", "3jo", "3joao");
        livro("Judas", "jd", "jud");
        livro("Apocalipse", "ap", "apo");
    }
}
