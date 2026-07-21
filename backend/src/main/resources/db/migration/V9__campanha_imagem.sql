-- ============================================================
-- V9: Imagens/artes anexadas a uma campanha (embutidas inline no e-mail).
-- ============================================================
CREATE TABLE campanha_imagem (
    id          BIGSERIAL,
    campanha_id BIGINT       NOT NULL,
    nome        VARCHAR(200),
    tipo        VARCHAR(100) NOT NULL,   -- content-type (image/png, image/jpeg, ...)
    conteudo    BYTEA        NOT NULL,
    ordem       INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_campanha_imagem PRIMARY KEY (id),
    CONSTRAINT fk_campanha_imagem_campanha FOREIGN KEY (campanha_id)
        REFERENCES campanha (id) ON DELETE CASCADE
);
CREATE INDEX idx_campanha_imagem_campanha ON campanha_imagem (campanha_id);
