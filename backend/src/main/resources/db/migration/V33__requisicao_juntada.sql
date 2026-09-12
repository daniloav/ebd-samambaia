-- ============================================================
-- V33: juntar requisições. Às vezes o líder abre duas requisições para a mesma
-- compra e, no fim, o valor de cada uma fica quebrado — o repasse (e a nota)
-- vem num único montante. Agora ele junta as requisições ABERTAS: uma vira a
-- principal (valor solicitado = soma) e as demais ficam com status 'JUNTADA',
-- apontando para ela (juntada_na_id). A absorvida MANTÉM o próprio valor, que
-- é o que permite desfazer a junção subtraindo exatamente o que entrou.
-- A coluna status é VARCHAR(12) sem CHECK, então 'JUNTADA' cabe sem alteração.
-- ============================================================
ALTER TABLE requisicao_tesouraria
    ADD COLUMN juntada_na_id BIGINT REFERENCES requisicao_tesouraria (id) ON DELETE SET NULL;
ALTER TABLE requisicao_tesouraria ADD COLUMN juntada_em TIMESTAMP;

CREATE INDEX idx_requisicao_juntada_na ON requisicao_tesouraria (juntada_na_id);

-- View de integração: CREATE OR REPLACE exige MESMA ordem/nome das colunas já
-- existentes (só dá pra ACRESCENTAR no fim). Mantemos tudo da V28 e adicionamos
-- juntada_na + qtd_juntadas ao final. Assim o GRANT do usuário read-only é
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
    EXISTS (SELECT 1 FROM requisicao_anexo x WHERE x.requisicao_id = r.id AND x.categoria = 'TROCO') AS possui_comprovante_troco,
    r.pix_titular,
    r.pix_beneficiario_nome,
    r.pix_beneficiario_obs,
    -- novas colunas (sempre no fim, por causa do CREATE OR REPLACE):
    (SELECT j.numero FROM requisicao_tesouraria j WHERE j.id = r.juntada_na_id) AS juntada_na,
    (SELECT COUNT(*) FROM requisicao_tesouraria x WHERE x.juntada_na_id = r.id)  AS qtd_juntadas
FROM requisicao_tesouraria r
JOIN      usuario su ON su.id = r.solicitante_id
LEFT JOIN aluno   sa ON sa.id = su.aluno_id
LEFT JOIN usuario au ON au.id = r.avaliado_por_id
LEFT JOIN aluno   aa ON aa.id = au.aluno_id;
