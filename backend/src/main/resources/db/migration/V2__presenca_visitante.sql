-- Adiciona o item "trouxe visitante" à chamada.
ALTER TABLE presenca ADD COLUMN trouxe_visitante BOOLEAN NOT NULL DEFAULT FALSE;
