-- ============================================================
-- V16: módulo de requisições da tesouraria. Líderes solicitam recursos,
-- tesoureiros aprovam/negam; ao usar, o líder finaliza anexando a nota fiscal.
-- ============================================================
CREATE TABLE requisicao_tesouraria (
    id                 BIGSERIAL PRIMARY KEY,
    numero             VARCHAR(20)  NOT NULL UNIQUE,          -- REQ-2026-0001
    solicitante_id     BIGINT       NOT NULL REFERENCES usuario (id),
    ministerio         VARCHAR(120) NOT NULL,
    nome_evento        VARCHAR(160),
    destinacao         VARCHAR(300) NOT NULL,
    motivo             TEXT         NOT NULL,
    valor_solicitado   NUMERIC(12,2) NOT NULL,
    data_necessidade   DATE,
    status             VARCHAR(12)  NOT NULL DEFAULT 'ABERTA', -- ABERTA|APROVADA|NEGADA|FINALIZADA|CANCELADA
    valor_aprovado     NUMERIC(12,2),
    parecer_tesoureiro VARCHAR(500),
    avaliado_por_id    BIGINT       REFERENCES usuario (id),
    avaliado_em        TIMESTAMP,
    valor_gasto        NUMERIC(12,2),
    observacao_final   VARCHAR(500),
    finalizado_em      TIMESTAMP,
    criado_em          TIMESTAMP    NOT NULL DEFAULT NOW(),
    nota_cobrada_em    DATE                                    -- dedup do lembrete diário
);
CREATE INDEX idx_requisicao_status ON requisicao_tesouraria (status);
CREATE INDEX idx_requisicao_solicitante ON requisicao_tesouraria (solicitante_id);

CREATE TABLE requisicao_anexo (
    id             BIGSERIAL PRIMARY KEY,
    requisicao_id  BIGINT       NOT NULL REFERENCES requisicao_tesouraria (id) ON DELETE CASCADE,
    nome           VARCHAR(200),
    tipo           VARCHAR(100) NOT NULL,
    conteudo       BYTEA        NOT NULL
);
CREATE INDEX idx_requisicao_anexo_req ON requisicao_anexo (requisicao_id);

-- Amplia o CHECK de role para os novos papéis da tesouraria.
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS ck_usuario_role;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_role
    CHECK (role IN ('ADMIN', 'PROFESSOR', 'ALUNO', 'TESOUREIRO', 'LIDER'));
