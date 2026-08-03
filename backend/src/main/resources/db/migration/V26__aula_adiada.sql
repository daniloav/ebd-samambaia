-- Aula adiada/cancelada: quando um evento da igreja cancela o encontro, a aula é marcada
-- como adiada e deixa de contar em qualquer pontuação/retrospecto (chamada, rankings,
-- relatórios, boletim, dashboard, frequência, inativação por faltas e promoção de visitante).
-- A agenda seguinte é empurrada +7 dias e uma aula de reposição é criada no domingo liberado.
alter table aula add column adiada boolean not null default false;
