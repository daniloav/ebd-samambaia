-- ============================================================
-- V20: forma de repasse do recurso na requisição (dinheiro ou pix) + a chave
-- pix (tipo + valor), e a categoria do anexo (nota fiscal x comprovante de
-- transferência que o tesoureiro anexa na aprovação). Atualiza a view de
-- integração para expor essas informações.
-- ============================================================
ALTER TABLE requisicao_tesouraria ADD COLUMN forma_repasse VARCHAR(10) NOT NULL DEFAULT 'DINHEIRO';
ALTER TABLE requisicao_tesouraria ADD COLUMN pix_tipo      VARCHAR(12);   -- CPF | EMAIL | TELEFONE (nunca ALEATORIA)
ALTER TABLE requisicao_tesouraria ADD COLUMN pix_chave     VARCHAR(140);

-- Categoria distingue a nota fiscal (prestação de contas, líder) do comprovante
-- de transferência (aprovação, tesoureiro). Anexos existentes são nota fiscal.
ALTER TABLE requisicao_anexo ADD COLUMN categoria VARCHAR(12) NOT NULL DEFAULT 'NOTA_FISCAL';

-- View de integração: CREATE OR REPLACE exige MESMA ordem/nome das colunas
-- existentes (só dá pra ACRESCENTAR no fim). Mantemos a ordem da V17, ajustamos
-- só a expressão de possui_nota_fiscal (agora por categoria) e adicionamos, ao
-- final, possui_comprovante + forma_repasse + pix_tipo + pix_chave. Assim o
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
    -- novas colunas (sempre no fim, por causa do CREATE OR REPLACE):
    EXISTS (SELECT 1 FROM requisicao_anexo x WHERE x.requisicao_id = r.id AND x.categoria = 'COMPROVANTE') AS possui_comprovante,
    r.forma_repasse,
    r.pix_tipo,
    r.pix_chave
FROM requisicao_tesouraria r
JOIN      usuario su ON su.id = r.solicitante_id
LEFT JOIN aluno   sa ON sa.id = su.aluno_id
LEFT JOIN usuario au ON au.id = r.avaliado_por_id
LEFT JOIN aluno   aa ON aa.id = au.aluno_id;
