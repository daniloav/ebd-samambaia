-- ============================================================
-- V6: RBAC — vínculo N:N entre PROFESSOR (usuario) e Classe.
-- Um professor pode ter várias classes; uma classe pode ter vários professores.
-- ============================================================
CREATE TABLE professor_classe (
    usuario_id BIGINT NOT NULL,
    classe_id  BIGINT NOT NULL,
    CONSTRAINT pk_professor_classe PRIMARY KEY (usuario_id, classe_id),
    CONSTRAINT fk_pc_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_pc_classe  FOREIGN KEY (classe_id)  REFERENCES classe (id)  ON DELETE CASCADE
);

CREATE INDEX idx_pc_classe ON professor_classe (classe_id);
