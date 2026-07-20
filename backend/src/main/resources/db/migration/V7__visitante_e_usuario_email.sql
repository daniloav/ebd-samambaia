-- ============================================================
-- V7: Visitantes como cadastro (nome/e-mail/telefone + quem trouxe)
--     e e-mail no usuário (para notificar os professores).
-- ============================================================

-- E-mail do usuário (usado para avisar os professores sobre novos visitantes).
ALTER TABLE usuario ADD COLUMN email VARCHAR(150);

-- Visitantes de uma aula/EBD.
CREATE TABLE visitante (
    id            BIGSERIAL,
    aula_id       BIGINT       NOT NULL,
    nome          VARCHAR(120) NOT NULL,
    email         VARCHAR(150),
    telefone      VARCHAR(20),
    trazido_por   BIGINT,                 -- aluno que trouxe (opcional)
    data_cadastro TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_visitante PRIMARY KEY (id),
    CONSTRAINT fk_visitante_aula  FOREIGN KEY (aula_id)     REFERENCES aula (id)  ON DELETE CASCADE,
    CONSTRAINT fk_visitante_aluno FOREIGN KEY (trazido_por) REFERENCES aluno (id) ON DELETE SET NULL
);

CREATE INDEX idx_visitante_aula ON visitante (aula_id);
