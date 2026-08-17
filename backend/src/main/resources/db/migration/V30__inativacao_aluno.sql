-- ============================================================
-- V30: histórico de inativação de alunos.
-- Até aqui só existia o booleano aluno.ativo: dava para saber QUEM está inativo,
-- mas não QUANDO saiu, POR QUE saiu, nem quem já voltou. Cada episódio vira uma
-- linha: aberta quando o aluno é inativado e fechada (reativado_em) quando volta.
-- ============================================================
CREATE TABLE aluno_inativacao (
    id              BIGSERIAL PRIMARY KEY,
    aluno_id        BIGINT NOT NULL REFERENCES aluno (id) ON DELETE CASCADE,
    inativado_em    TIMESTAMP,             -- nulo só no histórico anterior a esta migration
    motivo          VARCHAR(20) NOT NULL,  -- FALTAS_SEGUIDAS | MANUAL | NAO_REGISTRADO
    faltas_seguidas INTEGER,
    inativado_por   VARCHAR(60),
    reativado_em    TIMESTAMP,
    reativado_por   VARCHAR(60)
);

CREATE INDEX ix_aluno_inativacao_aluno ON aluno_inativacao (aluno_id);
CREATE INDEX ix_aluno_inativacao_data ON aluno_inativacao (inativado_em);

-- Backfill: um episódio aberto para cada aluno hoje inativo. Quando a inativação foi
-- automática, a auditoria guarda quando e quantas faltas (mensagem do ChamadaService);
-- o resto (inativação manual antiga) entra como NAO_REGISTRADO, sem data.
INSERT INTO aluno_inativacao (aluno_id, inativado_em, motivo, faltas_seguidas, inativado_por)
SELECT a.id,
       aud.data_hora,
       CASE WHEN aud.data_hora IS NULL THEN 'NAO_REGISTRADO' ELSE 'FALTAS_SEGUIDAS' END,
       aud.faltas,
       aud.usuario
FROM aluno a
LEFT JOIN LATERAL (
    SELECT x.data_hora,
           x.usuario,
           NULLIF(substring(x.descricao FROM '([0-9]+) faltas seguidas'), '')::INTEGER AS faltas
    FROM auditoria x
    WHERE x.entidade = 'ALUNO'
      AND x.entidade_id = a.id
      AND x.descricao LIKE '%inativado(a) automaticamente%'
    ORDER BY x.data_hora DESC
    LIMIT 1
) aud ON TRUE
WHERE a.ativo = FALSE;
