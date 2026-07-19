package br.com.ice.ebd.dto;

import java.util.List;

/** Todos os rankings do módulo de desafios. */
public record DesafiosResponse(
        long totalAulas,
        long totalProvas,
        List<RankingItem> menosFaltou,
        List<RankingItem> maisTrouxeBiblia,
        List<RankingItem> maisTrouxeRevista,
        List<RankingItem> maisEstudouLicao,
        List<RankingItem> melhoresNotas) {
}
