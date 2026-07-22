-- ============================================================
-- V11: acesso automático do aluno. Todo aluno cadastrado passa a ter um
-- login (usuário ALUNO vinculado) com senha padrão, obrigando a troca no
-- 1º acesso. Este flag marca quem ainda precisa trocar a senha.
-- ============================================================
ALTER TABLE usuario ADD COLUMN precisa_trocar_senha BOOLEAN NOT NULL DEFAULT FALSE;
