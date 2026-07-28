-- ============================================================
-- V22: permite excluir um usuário que APENAS avaliou requisições — a referência
-- avaliado_por passa a ON DELETE SET NULL (a requisição é preservada, só perde o
-- vínculo do avaliador). O solicitante continua obrigatório (NOT NULL): quem ABRIU
-- requisições não pode ser excluído sem antes tratar essas requisições — o serviço
-- devolve um erro claro (409) em vez de estourar no banco.
-- ============================================================
ALTER TABLE requisicao_tesouraria DROP CONSTRAINT requisicao_tesouraria_avaliado_por_id_fkey;
ALTER TABLE requisicao_tesouraria ADD CONSTRAINT requisicao_tesouraria_avaliado_por_id_fkey
    FOREIGN KEY (avaliado_por_id) REFERENCES usuario (id) ON DELETE SET NULL;
