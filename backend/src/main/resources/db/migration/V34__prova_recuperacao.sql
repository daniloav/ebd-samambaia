-- Prova de RECUPERAÇÃO: um quiz de múltipla escolha vinculado a UMA aula, que devolve ao
-- aluno pontos de presença proporcionais à nota (nota máxima = 1 presença). Todos da turma
-- podem fazer — quem faltou e quem esteve presente —, com até 3 tentativas; vale a melhor.

-- 'RECUPERACAO' tem 11 caracteres e não cabia no VARCHAR(10) do tipo (V10).
alter table prova alter column tipo type varchar(12);

-- A aula que a recuperação cobre. Excluir a aula leva a recuperação junto (sem aula, a
-- pontuação não teria a que se referir).
alter table prova add column aula_id bigint references aula(id) on delete cascade;

create index idx_prova_aula on prova(aula_id);

-- No máximo uma recuperação por aula: duas somariam dois bônus para a mesma aula.
create unique index uq_prova_recuperacao_aula on prova(aula_id) where tipo = 'RECUPERACAO';

-- Várias tentativas por aluno: a unique (prova, aluno) da V10 vira (prova, aluno, tentativa).
-- As submissões que já existem são todas a 1ª tentativa.
alter table submissao add column tentativa smallint not null default 1;
alter table submissao drop constraint uq_submissao;
alter table submissao add constraint uq_submissao_tentativa unique (prova_id, aluno_id, tentativa);
