-- ============================================================
-- V29: lembrete de chamada pendente.
-- No dia da aula (não adiada), se a chamada ainda não foi feita, o professor recebe
-- um e-mail de hora em hora a partir das 12h. A coluna guarda o último disparo para
-- deduplicar dentro da mesma hora (protege reexecuções do scheduler).
-- ============================================================
ALTER TABLE aula ADD COLUMN chamada_cobrada_em TIMESTAMP;
