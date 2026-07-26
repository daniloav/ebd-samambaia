-- ============================================================
-- V14: estende a dedup de notificação a outros eventos que disparam e-mail.
-- Nota de prova: guarda a nota já notificada (só reenvia se a nota mudar).
-- Aniversário: guarda o dia já parabenizado (não reenvia no mesmo dia).
-- ============================================================
ALTER TABLE nota_prova ADD COLUMN notificada_nota NUMERIC(5,2);
ALTER TABLE aluno ADD COLUMN aniversario_notificado_em DATE;
