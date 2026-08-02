-- Estatísticas de uso (engajamento): registra cada login e o "último acesso" (last-seen)
-- de cada usuário. Base para o painel /uso (ADMIN): online agora, DAU/WAU/MAU, taxa de
-- ativação, dormentes, dispositivos, etc. Ver docs/ROADMAP.md (Estatísticas de uso A–G).

-- Last-seen: atualizado no login e no ping periódico do app (heartbeat leve).
ALTER TABLE usuario ADD COLUMN ultimo_acesso TIMESTAMP;

-- Um registro por login efetivo (autenticação bem-sucedida).
CREATE TABLE acesso_evento (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    data_hora   TIMESTAMP   NOT NULL DEFAULT now(),
    user_agent  VARCHAR(400)
);

CREATE INDEX ix_acesso_evento_data     ON acesso_evento (data_hora);
CREATE INDEX ix_acesso_evento_usuario  ON acesso_evento (usuario_id, data_hora);
