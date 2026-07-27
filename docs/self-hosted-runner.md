# Runner self-hosted (Docker numa VM da OCI)

Para **parar de queimar minutos** do GitHub Actions (repo privado = minutos medidos),
rodamos os jobs num **runner self-hosted** em container Docker, numa VM da OCI. Script:
[`scripts/setup-runner-oci.sh`](../scripts/setup-runner-oci.sh).

> **O que é seu (não faço por você):** provisionar a VM na OCI, gerar o token/PAT do
> GitHub e rodar o script na VM. O script e este runbook eu entrego; credenciais e infra
> são com você.

## ⚠️ Antes de tudo: dois pré-requisitos que mudam a decisão

1. **RAM — não use a VM do app.** As VMs atuais são **E2.1.Micro (1 GB)** e o build saiu
   delas de propósito (o GHCR existe porque 1 GB não builda Quarkus+Angular sem thrashing).
   O runner precisa **buildar imagens**, então rode numa **VM dedicada com ≥ 6–8 GB**.
   No Always Free isso é a **Ampere A1.Flex (ARM)**: o tier grátis dá **4 OCPU / 24 GB/mês**
   — dá pra uma A1 de **2 OCPU / 12 GB** só pro runner, a US$ 0. (A pegadinha é a
   **capacidade A1**, a mesma que você já enfrenta; Pay-As-You-Go costuma destravar.)

2. **Arquitetura — arm64 x x86.** Uma VM A1 é **arm64**; suas imagens de app hoje são
   **x86** (as E2.1.Micro são AMD). Então escolha:
   - **Manter o app x86** e, no runner arm64, **cross-buildar x86** com
     `docker buildx --platform linux/amd64` (+ QEMU). Funciona, mas o build Java sob
     emulação é mais lento.
   - **Migrar o app para arm64** (VMs viram A1) e buildar nativo — mais rápido, porém é
     uma migração maior.
   - Ou usar uma **VM de runner x86 paga** (PAYG, shape flex com RAM) — sai do grátis, mas
     evita emulação. Pode compensar vs. estourar minuto do GitHub.

## Passo 1 — Preparar a VM do runner (você, na OCI)

- Crie uma instância (recomendado: **A1.Flex, 2 OCPU / 12 GB, Ubuntu 24.04** — mesmo SO dos
  runners do GitHub). **Não** exponha portas de entrada além de SSH.
- Instale o Docker:
  ```bash
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker $USER   # reentre na sessão depois
  ```

## Passo 2 — Token do GitHub (você)

- **Modo persistente (simples):** GitHub → repo → **Settings → Actions → Runners →
  New self-hosted runner** → copie o **token de registro** (validade ~1h).
- **Modo efêmero (1 job por execução, mais isolado):** gere um **PAT** (fine-grained, escopo
  *Actions* / *Administration: read & write* no repo) — o script usa o PAT para emitir um
  token novo a cada job. Guarde o PAT no gerenciador; ele fica só na VM.

## Passo 3 — Rodar o script (na VM do runner)

```bash
# persistente
./scripts/setup-runner-oci.sh --repo daniloav/ebd-samambaia --token <TOKEN_DE_REGISTRO>

# efêmero (recomendado p/ isolamento; exige PAT)
./scripts/setup-runner-oci.sh --repo daniloav/ebd-samambaia --pat <PAT> --ephemeral \
  --labels self-hosted,linux,oci
```

O script builda uma imagem do **runner oficial** (nada de imagem de terceiros), monta o
`docker.sock` do host (para o runner buildar imagens) e sobe o container com `--restart=always`.
Acompanhe: `docker logs -f ebd-runner`.

## Passo 4 — Conferir

GitHub → **Settings → Actions → Runners**: o runner (`oci-<host>`) deve aparecer **Idle/online**.

## Passo 5 — Apontar os jobs para o runner

Só depois do runner **online**, troque nos workflows (`.github/workflows/*.yml`):
```yaml
# de:
runs-on: ubuntu-latest
# para:
runs-on: [self-hosted, linux, oci]
```
Dicas:
- Comece pelo **CD** (`cd.yml`) — é o que mais pesa. Pode deixar parte do CI no GitHub e só o
  build/deploy no runner, para não serializar tudo num runner só.
- Se o runner for **arm64** e o app continuar **x86**, o passo de build de imagem precisa de
  `docker buildx build --platform linux/amd64 ...` (e habilitar QEMU uma vez:
  `docker run --privileged --rm tonistiigi/binfmt --install amd64`).
- **Não** deixe jobs pendurados: se apontar para `self-hosted` e o runner cair, os jobs ficam
  na fila. Mantenha o container com `--restart=always` (o script já faz).

## 🔐 Segurança (importante)

- **Só em repo privado.** Runner self-hosted em repo **público** é RCE: qualquer PR de fork
  roda código arbitrário na sua VM. Este repo é privado — mantenha assim.
- O runner tem **acesso aos secrets** do repo e ao **`docker.sock`** do host (equivale a root
  na VM). Trate a VM do runner como sensível: só SSH, sem outros serviços, patch em dia.
- **Prefira efêmero** (`--ephemeral`): cada job num runner limpo, sem estado herdado entre execuções.
- Não reaproveite essa VM para outra coisa; se desativar o runner, remova o registro
  (`docker rm -f ebd-runner` + remover em Settings → Runners) e o PAT.

## Reverter

Para voltar ao runner do GitHub: reverta o `runs-on` para `ubuntu-latest` e remova o container
(`docker rm -f ebd-runner`). Nada no app depende do runner — é só onde o CI/CD executa.
