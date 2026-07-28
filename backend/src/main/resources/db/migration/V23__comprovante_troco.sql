-- ============================================================
-- V23: comprovante de devolução do troco. Quando o líder gasta menos que o
-- valor aprovado, o troco volta ao PIX da igreja; na finalização ele anexa o
-- comprovante dessa devolução (categoria 'TROCO' — cabe no VARCHAR(12) atual,
-- então NÃO altera a coluna, o que quebraria a view dependente/GRANT). Só
-- estende a view de integração com o flag possui_comprovante_troco.
-- ============================================================

-- View de integração: CREATE OR REPLACE exige MESMA ordem/nome das colunas já
-- existentes (só dá pra ACRESCENTAR no fim). Mantemos tudo da V20 e adicionamos
-- possui_comprovante_troco ao final. Assim o GRANT do usuário read-only é
-- preservado (DROP VIEW o perderia).
CREATE OR REPLACE VIEW vw_requisicoes_integracao AS
SELECT
    r.id                           AS requisicao_id,
    r.numero,
    r.status,
    r.ministerio,
    r.nome_evento,
    r.destinacao,
    r.motivo,
    r.valor_solicitado,
    r.valor_aprovado,
    r.valor_gasto,
    r.data_necessidade,
    COALESCE(sa.nome, su.username) AS solicitante,
    su.email                       AS solicitante_email,
    COALESCE(aa.nome, au.username) AS avaliado_por,
    r.avaliado_em,
    r.parecer_tesoureiro,
    r.observacao_final,
    r.finalizado_em,
    r.criado_em,
    (SELECT COUNT(*) FROM requisicao_anexo x WHERE x.requisicao_id = r.id) AS qtd_anexos,
    EXISTS (SELECT 1 FROM requisicao_anexo x WHERE x.requisicao_id = r.id AND x.categoria = 'NOTA_FISCAL') AS possui_nota_fiscal,
    EXISTS (SELECT 1 FROM requisicao_anexo x WHERE x.requisicao_id = r.id AND x.categoria = 'COMPROVANTE') AS possui_comprovante,
    r.forma_repasse,
    r.pix_tipo,
    r.pix_chave,
    -- nova coluna (sempre no fim, por causa do CREATE OR REPLACE):
    EXISTS (SELECT 1 FROM requisicao_anexo x WHERE x.requisicao_id = r.id AND x.categoria = 'TROCO') AS possui_comprovante_troco
FROM requisicao_tesouraria r
JOIN      usuario su ON su.id = r.solicitante_id
LEFT JOIN aluno   sa ON sa.id = su.aluno_id
LEFT JOIN usuario au ON au.id = r.avaliado_por_id
LEFT JOIN aluno   aa ON aa.id = au.aluno_id;
