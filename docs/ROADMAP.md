# Roadmap e backlog — EBD Adultos

Lista viva de próximos passos e ideias. Marque o que for concluindo e adicione novas ideias.

## 🔴 Prioridade imediata

- [ ] **Validar o runtime com Postgres real** (migrations, seed, login, chamada, rankings).
      Ainda não executado ponta-a-ponta. Ver CLAUDE.md §9.
- [ ] **Concluir o deploy na OCI** (aguardando capacidade A1; retry rodando) e publicar o site.
- [ ] Após deploy: **trocar as senhas padrão** (`admin`/`professor`) e as credenciais do banco.

## 🟡 Evoluções funcionais (curto prazo)

- [ ] **Dashboard com gráficos** (frequência ao longo do tempo, distribuição de presença).
- [ ] **Exportar relatório** de presenças para PDF/Excel.
- [ ] **Filtro por trimestre/período letivo** em relatórios e rankings.
- [ ] **Histórico da chamada** por aluno (linha do tempo de presença/itens).
- [ ] **Gestão de usuários** na UI (hoje só via seed): tela de CRUD de `Usuario` (ADMIN).
- [ ] **Aniversariantes do mês** (já temos `data_nascimento`).
- [ ] **Observações por aula/aluno** (campo de texto livre na chamada).

## 🟢 Qualidade e robustez

- [ ] **Testes automatizados**: `@QuarkusTest` para `ChamadaService`, `RelatorioService`,
      `DesafiosService`; testes de fluxo de autenticação.
- [ ] **Testes de front** (ao menos smoke dos serviços/guards).
- [ ] **CI** (GitHub Actions): build backend + frontend a cada push.
- [ ] **Soft-delete de aluno** (preservar histórico usando `ativo`, sem cascata destrutiva).
- [ ] **Paginação** nas listas quando crescer o volume.

## 🔵 Infra e segurança

- [ ] **HTTPS** na VM (Caddy ou certbot) com domínio próprio.
- [ ] **Backups automáticos** do Postgres (cron + `pg_dump`).
- [ ] **Chaves JWT persistentes** via volume (hoje o Docker gera novas a cada build).
- [ ] (Opcional) **Purga de histórico do Git** para remover a chave JWT antiga, se o repo virar público.
- [ ] **Budget/alerta de custo** na OCI (garantia extra contra cobrança).
- [ ] **Proteção de branch na `main`** (bloquear push direto, exigir PR + CI verde).
      Requer **GitHub Pro** (repo privado) ou tornar o repo **público**. Hoje o fluxo
      develop→PR→main é seguido por convenção. Aplicar via `gh api PUT .../branches/main/protection`.
- [ ] **Avaliar upgrade para "Pay As You Go"** para destravar capacidade do **A1** (ARM, 6 GB).
      Contas Free Trial têm baixa prioridade na fila do A1 ("Out of capacity"); o upgrade
      costuma liberar, e os recursos **Always Free continuam US$ 0**. Precisa cadastrar cartão
      (blindar com Budget). Alternativa ao E2.1.Micro (1 GB) que estamos usando agora.

## 💡 Ideias maiores (longo prazo)

- [ ] **PWA / uso offline** para fazer chamada sem internet e sincronizar depois.
- [ ] **Múltiplas classes/turmas** (hoje é só a classe de adultos).
- [ ] **Notificações** (WhatsApp/e-mail) para faltas seguidas ou aniversários.
- [ ] **Premiação automática** ao fim do trimestre com base nos rankings.

## Limitações conhecidas (estado atual)

- Exclusão de aluno/aula/prova é **destrutiva** (cascata).
- Rankings/relatórios consideram todos os alunos ativos e todo o período (sem recorte por turma/trimestre).
- Sem testes automatizados; validação só por build.
- Uma única VM roda tudo (db + api + front) — suficiente para o MVP, não para alta disponibilidade.
