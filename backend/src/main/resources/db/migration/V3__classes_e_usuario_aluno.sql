-- ============================================================
-- V3: Classes (multi-turma) + vínculo usuário↔aluno e role ALUNO
-- ============================================================

-- 1) Tabela de classes
CREATE TABLE classe (
    id            BIGSERIAL,
    nome          VARCHAR(120) NOT NULL,
    descricao     VARCHAR(300),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    data_cadastro TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_classe PRIMARY KEY (id),
    CONSTRAINT uq_classe_nome UNIQUE (nome)
);

-- classe padrão para os dados já existentes
INSERT INTO classe (nome, descricao) VALUES ('Adultos', 'Classe de adultos (turma inicial do MVP)');

-- 2) aluno.classe_id
ALTER TABLE aluno ADD COLUMN classe_id BIGINT;
UPDATE aluno SET classe_id = (SELECT id FROM classe WHERE nome = 'Adultos');
ALTER TABLE aluno ALTER COLUMN classe_id SET NOT NULL;
ALTER TABLE aluno ADD CONSTRAINT fk_aluno_classe FOREIGN KEY (classe_id) REFERENCES classe (id);
CREATE INDEX idx_aluno_classe ON aluno (classe_id);

-- 3) aula.classe_id  (uma aula pertence a uma classe; unicidade passa a ser por classe+data)
ALTER TABLE aula ADD COLUMN classe_id BIGINT;
UPDATE aula SET classe_id = (SELECT id FROM classe WHERE nome = 'Adultos');
ALTER TABLE aula ALTER COLUMN classe_id SET NOT NULL;
ALTER TABLE aula ADD CONSTRAINT fk_aula_classe FOREIGN KEY (classe_id) REFERENCES classe (id);
ALTER TABLE aula DROP CONSTRAINT IF EXISTS uq_aula_data;
ALTER TABLE aula ADD CONSTRAINT uq_aula_classe_data UNIQUE (classe_id, data);
CREATE INDEX idx_aula_classe ON aula (classe_id);

-- 4) prova.classe_id
ALTER TABLE prova ADD COLUMN classe_id BIGINT;
UPDATE prova SET classe_id = (SELECT id FROM classe WHERE nome = 'Adultos');
ALTER TABLE prova ALTER COLUMN classe_id SET NOT NULL;
ALTER TABLE prova ADD CONSTRAINT fk_prova_classe FOREIGN KEY (classe_id) REFERENCES classe (id);
CREATE INDEX idx_prova_classe ON prova (classe_id);

-- 5) usuario.aluno_id (opcional — vincula um login ao aluno; usado pela role ALUNO)
ALTER TABLE usuario ADD COLUMN aluno_id BIGINT;
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id) ON DELETE SET NULL;

-- 6) role ALUNO passa a ser permitida
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS ck_usuario_role;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_role CHECK (role IN ('ADMIN', 'PROFESSOR', 'ALUNO'));
