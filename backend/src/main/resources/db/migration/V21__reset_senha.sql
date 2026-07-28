-- ============================================================
-- V21: tokens de redefinição de senha (fluxo "esqueci minha senha/usuário").
-- Guarda o HASH do token (nunca o token em claro); expira e é de uso único.
-- ============================================================
CREATE TABLE reset_senha (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT       NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,   -- sha-256 em hex
    expira_em  TIMESTAMP    NOT NULL,
    usado_em   TIMESTAMP,
    criado_em  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reset_senha_usuario ON reset_senha (usuario_id);
