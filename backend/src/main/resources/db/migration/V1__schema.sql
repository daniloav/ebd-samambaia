-- ============================================================
-- Schema inicial da EBD Adultos (ICEV Samambaia)
-- ============================================================

-- Usuários do sistema (login / perfis)
CREATE TABLE usuario (
    id            BIGSERIAL,
    username      VARCHAR(60)  NOT NULL,
    senha_hash    VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    data_cadastro TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uq_usuario_username UNIQUE (username),
    CONSTRAINT ck_usuario_role CHECK (role IN ('ADMIN', 'PROFESSOR'))
);

-- Alunos da classe
CREATE TABLE aluno (
    id               BIGSERIAL,
    nome             VARCHAR(120) NOT NULL,
    telefone         VARCHAR(20),
    data_nascimento  DATE,
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    data_cadastro    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_aluno PRIMARY KEY (id)
);

-- Aulas (cada domingo/encontro da EBD)
CREATE TABLE aula (
    id    BIGSERIAL,
    data  DATE         NOT NULL,
    tema  VARCHAR(200),
    CONSTRAINT pk_aula PRIMARY KEY (id),
    CONSTRAINT uq_aula_data UNIQUE (data)
);

-- Chamada: presença + itens avaliados por aluno em cada aula
CREATE TABLE presenca (
    id             BIGSERIAL,
    aula_id        BIGINT  NOT NULL,
    aluno_id       BIGINT  NOT NULL,
    presente       BOOLEAN NOT NULL DEFAULT FALSE,
    trouxe_biblia  BOOLEAN NOT NULL DEFAULT FALSE,
    trouxe_revista BOOLEAN NOT NULL DEFAULT FALSE,
    estudou_licao  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_presenca PRIMARY KEY (id),
    CONSTRAINT uq_presenca_aula_aluno UNIQUE (aula_id, aluno_id),
    CONSTRAINT fk_presenca_aula  FOREIGN KEY (aula_id)  REFERENCES aula (id)  ON DELETE CASCADE,
    CONSTRAINT fk_presenca_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id) ON DELETE CASCADE
);

-- Provas (submódulo do módulo de desafios)
CREATE TABLE prova (
    id          BIGSERIAL,
    titulo      VARCHAR(200)  NOT NULL,
    data        DATE          NOT NULL,
    nota_maxima NUMERIC(5,2)  NOT NULL DEFAULT 10.00,
    CONSTRAINT pk_prova PRIMARY KEY (id),
    CONSTRAINT ck_prova_nota_maxima CHECK (nota_maxima > 0)
);

-- Notas de cada aluno em cada prova
CREATE TABLE nota_prova (
    id       BIGSERIAL,
    prova_id BIGINT       NOT NULL,
    aluno_id BIGINT       NOT NULL,
    nota     NUMERIC(5,2) NOT NULL,
    CONSTRAINT pk_nota_prova PRIMARY KEY (id),
    CONSTRAINT uq_nota_prova UNIQUE (prova_id, aluno_id),
    CONSTRAINT ck_nota_prova_nota CHECK (nota >= 0),
    CONSTRAINT fk_nota_prova_prova FOREIGN KEY (prova_id) REFERENCES prova (id) ON DELETE CASCADE,
    CONSTRAINT fk_nota_prova_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id) ON DELETE CASCADE
);

-- Índices para consultas de relatório/ranking
CREATE INDEX idx_presenca_aluno ON presenca (aluno_id);
CREATE INDEX idx_presenca_aula  ON presenca (aula_id);
CREATE INDEX idx_nota_aluno     ON nota_prova (aluno_id);
CREATE INDEX idx_aula_data      ON aula (data);
