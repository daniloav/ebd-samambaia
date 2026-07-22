-- ============================================================
-- V10: Prova online (quiz auto-corrigido). A prova ganha um tipo
-- (OFFLINE = nota lançada à mão, como hoje; ONLINE = quiz respondido pelo aluno)
-- e uma janela de disponibilidade. Questões + alternativas + submissões.
-- ============================================================
ALTER TABLE prova
    ADD COLUMN tipo     VARCHAR(10) NOT NULL DEFAULT 'OFFLINE',  -- OFFLINE | ONLINE
    ADD COLUMN abre_em  TIMESTAMP,
    ADD COLUMN fecha_em TIMESTAMP;

CREATE TABLE questao (
    id        BIGSERIAL PRIMARY KEY,
    prova_id  BIGINT       NOT NULL REFERENCES prova (id) ON DELETE CASCADE,
    enunciado TEXT         NOT NULL,
    tipo      VARCHAR(10)  NOT NULL,                 -- MULTIPLA | VF
    pontos    NUMERIC(5,2) NOT NULL DEFAULT 1.00,
    ordem     INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_questao_prova ON questao (prova_id);

CREATE TABLE alternativa (
    id         BIGSERIAL PRIMARY KEY,
    questao_id BIGINT       NOT NULL REFERENCES questao (id) ON DELETE CASCADE,
    texto      VARCHAR(500) NOT NULL,
    correta    BOOLEAN      NOT NULL DEFAULT FALSE,
    ordem      INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_alternativa_questao ON alternativa (questao_id);

CREATE TABLE submissao (
    id         BIGSERIAL PRIMARY KEY,
    prova_id   BIGINT       NOT NULL REFERENCES prova (id) ON DELETE CASCADE,
    aluno_id   BIGINT       NOT NULL REFERENCES aluno (id) ON DELETE CASCADE,
    enviada_em TIMESTAMP    NOT NULL DEFAULT NOW(),
    nota       NUMERIC(5,2) NOT NULL,
    CONSTRAINT uq_submissao UNIQUE (prova_id, aluno_id)   -- 1 tentativa por aluno
);
CREATE INDEX idx_submissao_prova ON submissao (prova_id);

CREATE TABLE resposta (
    id             BIGSERIAL PRIMARY KEY,
    submissao_id   BIGINT NOT NULL REFERENCES submissao (id) ON DELETE CASCADE,
    questao_id     BIGINT NOT NULL REFERENCES questao (id) ON DELETE CASCADE,
    alternativa_id BIGINT REFERENCES alternativa (id) ON DELETE SET NULL
);
CREATE INDEX idx_resposta_submissao ON resposta (submissao_id);
