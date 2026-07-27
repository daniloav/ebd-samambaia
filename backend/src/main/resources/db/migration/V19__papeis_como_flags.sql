-- ============================================================
-- V19: achata os papéis em capacidades (flags). ADMIN/PROFESSOR/ALUNO deixam
-- de ser a "role base" única e viram flags, como TESOUREIRO/LIDER já são (V18).
-- Assim um único usuário pode acumular professor + aluno (+ tesouraria).
-- ============================================================
ALTER TABLE usuario ADD COLUMN eh_admin     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN eh_professor BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN eh_aluno     BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill 1:1 a partir da role atual (sem perda de acesso).
UPDATE usuario SET eh_admin     = TRUE WHERE role = 'ADMIN';
UPDATE usuario SET eh_professor = TRUE WHERE role = 'PROFESSOR';
UPDATE usuario SET eh_aluno     = TRUE WHERE role = 'ALUNO';

-- A role deixa de existir; o CHECK e a coluna saem.
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS ck_usuario_role;
ALTER TABLE usuario DROP COLUMN role;
