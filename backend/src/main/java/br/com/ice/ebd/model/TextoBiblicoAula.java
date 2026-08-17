package br.com.ice.ebd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Leitura bíblica diária de uma lição: uma referência (ex.: "Salmos 1.1-6") para um dia da
 * semana da <b>semana que antecede</b> a aula. O cadastro é opcional — a aula pode ter de
 * zero a sete leituras, no máximo uma por dia da semana.
 *
 * <p>{@link #textoCache} guarda o texto bíblico buscado na internet, para não consultar a API
 * a cada envio; {@link #enviadoEm} deduplica o disparo diário das 12h.
 */
@Entity
@Table(name = "aula_texto_biblico")
public class TextoBiblicoAula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aula_id", nullable = false)
    private Aula aula;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DiaSemanaLeitura diaSemana;

    @Column(nullable = false, length = 200)
    private String referencia;

    /** Texto bíblico já buscado para esta referência (null enquanto não foi buscado). */
    @Column(name = "texto_cache", columnDefinition = "text")
    private String textoCache;

    @Column(name = "texto_cache_em")
    private LocalDateTime textoCacheEm;

    /** Dia em que o e-mail desta leitura foi enviado (dedup do batch diário). */
    @Column(name = "enviado_em")
    private LocalDate enviadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Aula getAula() { return aula; }
    public void setAula(Aula aula) { this.aula = aula; }

    public DiaSemanaLeitura getDiaSemana() { return diaSemana; }
    public void setDiaSemana(DiaSemanaLeitura diaSemana) { this.diaSemana = diaSemana; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getTextoCache() { return textoCache; }
    public void setTextoCache(String textoCache) { this.textoCache = textoCache; }

    public LocalDateTime getTextoCacheEm() { return textoCacheEm; }
    public void setTextoCacheEm(LocalDateTime textoCacheEm) { this.textoCacheEm = textoCacheEm; }

    public LocalDate getEnviadoEm() { return enviadoEm; }
    public void setEnviadoEm(LocalDate enviadoEm) { this.enviadoEm = enviadoEm; }

    /** Data em que esta leitura deve ser enviada (dia da semana anterior à aula). */
    public LocalDate dataDaLeitura() {
        return diaSemana.dataAntesDe(aula.getData());
    }
}
