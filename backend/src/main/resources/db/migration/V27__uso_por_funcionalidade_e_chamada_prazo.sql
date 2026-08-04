-- Estatísticas de uso — lote 2 (itens D e F do roadmap A–G; ver docs/ROADMAP.md).
--
-- D) Uso por funcionalidade: até aqui só o login era instrumentado (acesso_evento).
--    Esta tabela registra o uso das telas/ações do app (page views e cliques notáveis),
--    alimentando o "feature mais usada" do painel /uso.
--
-- F) Chamada no prazo × atrasada: a presença não guardava QUANDO foi registrada, só o
--    conteúdo. A coluna registrada_em marca o momento em que a chamada daquela aula foi
--    lançada (comparado com a data da aula → no prazo vs. atrasada). Linhas antigas ficam
--    nulas (contam como "sem data").

-- D) Eventos de uso de funcionalidade (page view / clique). Um por interação instrumentada.
CREATE TABLE uso_evento (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    data_hora   TIMESTAMP   NOT NULL DEFAULT now(),
    recurso     VARCHAR(60) NOT NULL,             -- ex.: chamada, desafios, boletim, aniversariantes
    acao        VARCHAR(20) NOT NULL DEFAULT 'ABRIR' -- ABRIR (abriu a tela) | CLICAR (ação notável)
);

CREATE INDEX ix_uso_evento_data    ON uso_evento (data_hora);
CREATE INDEX ix_uso_evento_recurso ON uso_evento (recurso, data_hora);

-- F) Quando a chamada daquela aula foi efetivamente registrada (1º lançamento).
ALTER TABLE presenca ADD COLUMN registrada_em TIMESTAMP;
