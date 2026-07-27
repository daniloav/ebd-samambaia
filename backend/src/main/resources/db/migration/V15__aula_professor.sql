-- ============================================================
-- V15: professor da aula. A aula passa a registrar qual professor (usuário
-- PROFESSOR) deu a aula. Quando gravado, o aluno vinculado a esse professor
-- fica desabilitado na chamada e não pontua no ranking daquela aula.
-- ============================================================
ALTER TABLE aula ADD COLUMN professor_id BIGINT REFERENCES usuario (id) ON DELETE SET NULL;
CREATE INDEX idx_aula_professor ON aula (professor_id);
