package br.com.ice.ebd.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Referência bíblica escrita à mão pelo professor ("Sl 1.1-6", "1Jo 4.7-8", "Salmos 119"):
 * <b>valida</b> o que foi digitado e <b>normaliza</b> para o formato que a API de textos
 * entende (livro por extenso + {@code capítulo:versículo}).
 *
 * <p>Fonte única do conhecimento dos 66 livros — a mesma tabela serve para reconhecer o livro
 * na validação do cadastro e para montar a consulta em {@link BibliaOnlineService}.
 */
public final class ReferenciaBiblica {

    private ReferenciaBiblica() {
    }

    /** Máximo de referências no mesmo dia ("Sl 1.1-6; Pv 3.5-6"). */
    public static final int MAX_REFERENCIAS = 3;

    /**
     * Estrutura aceita: livro (abreviado ou por extenso, podendo começar por 1/2/3), capítulo e,
     * opcionalmente, versículo ou intervalo — separados por {@code .}, {@code :} ou {@code ,}.
     * Ex.: {@code Sl 1.1-6}, {@code 1Jo 4.7}, {@code Salmos 119}, {@code 1 Coríntios 13:4-7}.
     */
    private static final Pattern FORMATO = Pattern.compile(
            "^([123]\\s*)?\\p{L}[\\p{L}.]*(\\s+\\p{L}[\\p{L}.]*)*"
                    + "\\s*\\d{1,3}([.:,]\\s*\\d{1,3}(\\s*-\\s*\\d{1,3})?)?$");

    /** Exemplo mostrado em toda mensagem de erro (o professor vê o formato certo na hora). */
    private static final String EXEMPLOS = "ex.: Sl 1.1-6, 1Jo 4.7-8, Salmos 119";

    /**
     * Valida a referência do cadastro (uma ou mais, separadas por {@code ;}). Devolve a mensagem
     * de erro quando algo não bate, ou {@link Optional#empty()} quando está tudo certo.
     *
     * <p>Além do formato, o <b>livro precisa ser conhecido</b>: é isso que pega erro de digitação
     * ("Slm 1.1"), que passaria por qualquer regex mas não traria texto nenhum no e-mail.
     */
    public static Optional<String> validar(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return Optional.empty(); // dia sem leitura é válido — o cadastro é opcional
        }
        String[] partes = referencia.split(";");
        if (partes.length > MAX_REFERENCIAS) {
            return Optional.of("no máximo " + MAX_REFERENCIAS + " referências por dia (separadas por \";\")");
        }
        for (String parte : partes) {
            String ref = parte.trim().replaceAll("\\s+", " ");
            if (ref.isEmpty()) {
                return Optional.of("há um \";\" sobrando (referência vazia)");
            }
            if (!FORMATO.matcher(ref).matches()) {
                return Optional.of("\"" + ref + "\" não parece uma referência bíblica (" + EXEMPLOS + ")");
            }
            if (livroDe(ref) == null) {
                return Optional.of("não reconheço o livro em \"" + ref
                        + "\" — use a abreviação (Sl, 1Jo, Pv) ou o nome por extenso");
            }
        }
        return Optional.empty();
    }

    /** Nome por extenso do livro citado na referência, ou null se o livro não é conhecido. */
    private static String livroDe(String referencia) {
        String ref = referencia.replaceAll("^([123])\\s*(?=\\p{L})", "$1 ");
        int corte = ref.lastIndexOf(' ');
        String livro = corte > 0 ? ref.substring(0, corte) : ref.replaceAll("[0-9].*$", "");
        String porExtenso = LIVROS_ACENTUADOS.get(chaveComAcento(livro));
        return porExtenso != null ? porExtenso : LIVROS.get(chave(livro));
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
