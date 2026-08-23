-- Vínculo da aula de reposição com a aula adiada que a originou. Sem ele não dá para
-- "retirar o adiamento": o desfazer precisa saber qual aula foi criada pelo adiamento
-- para removê-la e trazer a agenda da turma de volta -7 dias.
alter table aula add column reposicao_de_id bigint references aula(id) on delete set null;

create index idx_aula_reposicao_de on aula(reposicao_de_id);

-- Backfill best-effort das aulas adiadas que já existem: a reposição criada pelo adiamento
-- fica na mesma turma, 7 dias depois e herdando o tema. Quem não casar (tema editado, agenda
-- remanejada depois) simplesmente fica sem vínculo — aí o desfazer só tira a marca de adiada
-- e mantém a reposição, sem excluir nada por adivinhação.
update aula r
   set reposicao_de_id = o.id
  from aula o
 where o.adiada = true
   and r.id <> o.id
   and r.classe_id = o.classe_id
   and r.data = o.data + 7
   and r.adiada = false
   and r.reposicao_de_id is null
   and r.tema is not distinct from o.tema;
