-- ============================================================
-- V18: tesoureiro e líder deixam de ser ROLE base e viram CAPACIDADES
-- (flags) que qualquer usuário pode acumular. Um professor ou aluno pode
-- ser tesoureiro/líder; o ADMIN recebe as capacidades por padrão (no token).
-- ============================================================
ALTER TABLE usuario ADD COLUMN eh_tesoureiro BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN eh_lider      BOOLEAN NOT NULL DEFAULT FALSE;

-- Migra quem tinha a role funcional para a flag correspondente e cai numa
-- role base neutra (PROFESSOR: não exige vínculo com aluno). Em produção este
-- módulo ainda não subiu, então normalmente não há linhas a converter.
UPDATE usuario SET eh_tesoureiro = TRUE WHERE role = 'TESOUREIRO';
UPDATE usuario SET eh_lider      = TRUE WHERE role = 'LIDER';
UPDATE usuario SET role = 'PROFESSOR' WHERE role IN ('TESOUREIRO', 'LIDER');

-- CHECK volta a permitir só as roles base.
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS ck_usuario_role;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_role
    CHECK (role IN ('ADMIN', 'PROFESSOR', 'ALUNO'));
