-- ============================================================
-- V13: dedup de notificação da chamada. Guarda por presença a "assinatura"
-- do que já foi notificado (presente + itens). Ao re-salvar a mesma chamada,
-- só reenvia e-mail para quem é novo ou mudou de estado.
-- ============================================================
ALTER TABLE presenca ADD COLUMN notificada_assinatura VARCHAR(16);
