-- Alertas por e-mail: contato e opt-in (LGPD) por aluno.
ALTER TABLE aluno ADD COLUMN email VARCHAR(150);
ALTER TABLE aluno ADD COLUMN recebe_notificacoes BOOLEAN NOT NULL DEFAULT FALSE;
