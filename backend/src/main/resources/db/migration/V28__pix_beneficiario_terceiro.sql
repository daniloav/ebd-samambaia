-- ============================================================
-- V28: a chave PIX da requisição pode ser de um TERCEIRO (oferta de amor).
-- Até aqui a chave tinha de ser do próprio solicitante; quando a igreja ajuda
-- um irmão em necessidade, o dinheiro vai direto para a conta dele, então a
-- chave é do beneficiado. Guardamos de quem é a chave (pix_titular) e, quando
-- é de terceiro, o nome do beneficiário (obrigatório na aplicação) e uma
-- observação livre para a tesouraria (quem é / por quê).
-- Requisições existentes são todas do próprio solicitante -> DEFAULT 'PROPRIO'.
-- ============================================================
ALTER TABLE requisicao_tesouraria ADD COLUMN pix_titular VARCHAR(10) NOT NULL DEFAULT 'PROPRIO'; -- PROPRIO | TERCEIRO
ALTER TABLE requisicao_tesouraria ADD COLUMN pix_beneficiario_nome VARCHAR(160);
ALTER TABLE requisicao_tesouraria ADD COLUMN pix_beneficiario_obs  VARCHAR(300);

-- View de integração: CREATE OR REPLACE exige MESMA ordem/nome das colunas já
-- existentes (só dá pra ACRESCENTAR no fim). Mantemos tudo da V23 e adicionamos
-- pix_titular + pix_beneficiario_nome + pix_beneficiario_obs ao final. Assim o
-- GRANT do usuário read-only é preservado (DROP VIEW o perderia).
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
    EXISTS (SELECT 1 FROM requisicao_anexo x WHERE x.requisicao_id = r.id AND x.categoria = 'TROCO') AS possui_comprovante_troco,
    -- novas colunas (sempre no fim, por causa do CREATE OR REPLACE):
    r.pix_titular,
    r.pix_beneficiario_nome,
    r.pix_beneficiario_obs
FROM requisicao_tesouraria r
JOIN      usuario su ON su.id = r.solicitante_id
LEFT JOIN aluno   sa ON sa.id = su.aluno_id
LEFT JOIN usuario au ON au.id = r.avaliado_por_id
LEFT JOIN aluno   aa ON aa.id = au.aluno_id;
