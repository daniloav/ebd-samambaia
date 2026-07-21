package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Campanha;
import br.com.ice.ebd.model.CampanhaImagem;
import java.time.LocalDateTime;
import java.util.List;

public record CampanhaResponse(
        Long id,
        String titulo,
        String mensagem,
        Long classeId,
        String classeNome,
        int totalEnviados,
        String criadoPor,
        LocalDateTime dataEnvio,
        List<ImagemMeta> imagens) {

    /** Metadados de uma imagem (sem o conteúdo binário). */
    public record ImagemMeta(Long id, String nome, String tipo) {
        public static ImagemMeta de(CampanhaImagem i) {
            return new ImagemMeta(i.getId(), i.getNome(), i.getTipo());
        }
    }

    public static CampanhaResponse de(Campanha c, List<ImagemMeta> imagens) {
        return new CampanhaResponse(
                c.getId(), c.getTitulo(), c.getMensagem(),
                c.getClasse() != null ? c.getClasse().getId() : null,
                c.getClasse() != null ? c.getClasse().getNome() : "Todas as turmas",
                c.getTotalEnviados(), c.getCriadoPor(), c.getDataEnvio(),
                imagens != null ? imagens : List.of());
    }

    public static CampanhaResponse de(Campanha c) {
        return de(c, List.<ImagemMeta>of());
    }
}
