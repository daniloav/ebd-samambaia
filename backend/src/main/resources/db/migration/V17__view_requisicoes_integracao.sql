-- ============================================================
-- V17: view somente-leitura para integração externa da tesouraria.
-- Expõe as requisições em formato "achatado" e legível (nomes resolvidos,
-- contagem de anexos) para um sistema de terceiros consumir por SELECT.
-- NÃO expõe senha_hash nem o conteúdo binário das notas fiscais (bytea).
-- O usuário read-only e os GRANTs são criados fora do versionamento
-- (ver docs/integracao-tesouraria.md) — aqui só mora a estrutura da view.
-- ============================================================
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
    EXISTS (SELECT 1 FROM requisicao_anexo x WHERE x.requisicao_id = r.id) AS possui_nota_fiscal
FROM requisicao_tesouraria r
JOIN      usuario su ON su.id = r.solicitante_id
LEFT JOIN aluno   sa ON sa.id = su.aluno_id
LEFT JOIN usuario au ON au.id = r.avaliado_por_id
LEFT JOIN aluno   aa ON aa.id = au.aluno_id;

COMMENT ON VIEW vw_requisicoes_integracao IS
    'Somente-leitura para integracao externa (tesouraria). Sem senhas nem anexos binarios.';
