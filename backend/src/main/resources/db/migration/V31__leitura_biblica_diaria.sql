-- ============================================================
-- V31: leituras bíblicas diárias da lição.
-- Cada aula pode ter (opcionalmente) um texto bíblico por dia da semana — as
-- "leituras diárias" da revista. Elas pertencem à SEMANA ANTERIOR ao dia da aula
-- (preparação): para a aula de domingo 23/08, a leitura de segunda é 17/08 e a de
-- sábado é 22/08. Todo dia às 12h (BRT) o texto do dia é enviado por e-mail aos
-- alunos da turma que optaram por receber avisos.
--
-- texto_cache guarda o texto bíblico buscado na internet (bible-api.com, Almeida
-- domínio público): busca-se uma vez por referência e reaproveita-se no e-mail.
-- enviado_em deduplica o disparo do dia (protege reexecução do scheduler).
-- ============================================================
CREATE TABLE aula_texto_biblico (
    id             BIGSERIAL PRIMARY KEY,
    aula_id        BIGINT NOT NULL REFERENCES aula (id) ON DELETE CASCADE,
    dia_semana     VARCHAR(10) NOT NULL,
    referencia     VARCHAR(200) NOT NULL,
    texto_cache    TEXT,
    texto_cache_em TIMESTAMP,
    enviado_em     DATE,
    CONSTRAINT uq_aula_texto_dia UNIQUE (aula_id, dia_semana),
    CONSTRAINT ck_aula_texto_dia CHECK (dia_semana IN
        ('DOMINGO', 'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO'))
);

CREATE INDEX ix_aula_texto_aula ON aula_texto_biblico (aula_id);
CREATE INDEX ix_aula_texto_dia ON aula_texto_biblico (dia_semana);
