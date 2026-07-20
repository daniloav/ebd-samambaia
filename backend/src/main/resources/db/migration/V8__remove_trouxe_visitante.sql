-- ============================================================
-- V8: Unifica "visitante" no cadastro (tabela visitante, V7).
-- Remove o booleano trouxe_visitante da presença — a fonte única passa a ser
-- o cadastro de visitantes (com "trazido por"). Rankings e relatórios contam de lá.
-- ============================================================
ALTER TABLE presenca DROP COLUMN trouxe_visitante;
