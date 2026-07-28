-- ============================================================
-- V24: falta justificada. O aluno pode justificar uma aula em que faltou
-- (linha de presença com presente=false), pela própria área. Uma falta
-- justificada vale 30% dos pontos de uma presença no ranking (peso 0,3) e
-- NÃO conta como falta para a regra de inativação por faltas seguidas.
-- ============================================================
ALTER TABLE presenca ADD COLUMN justificada BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE presenca ADD COLUMN justificativa_motivo VARCHAR(300);
ALTER TABLE presenca ADD COLUMN justificada_em TIMESTAMP;
