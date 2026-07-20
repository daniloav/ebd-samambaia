# Como disponibilizar e instalar o app (PWA)

O EBD Adultos é um **PWA** (Progressive Web App): é o próprio site
**https://ebd-ices.duckdns.org**, que pode ser **instalado** no celular ou no
computador e passa a abrir como um aplicativo (ícone na tela inicial, tela cheia,
sem a barra do navegador).

> **Não há loja de aplicativos.** Você não publica nada na Play Store nem na App
> Store, não paga taxa de loja e não envia arquivo (APK/IPA). Para "distribuir",
> basta **compartilhar o link** — cada pessoa instala pelo próprio navegador.

---

## 1. Pré-requisitos (do lado do servidor)

Já estão todos atendidos quando a versão com PWA está no ar:

- ✅ Site servido por **HTTPS** (obrigatório para PWA) — já temos, via Caddy/Let's Encrypt.
- ✅ Arquivos do PWA publicados: `manifest.webmanifest`, `sw.js` e `assets/icon.svg`.

> ⚠️ **Importante:** o PWA só fica ativo em produção **depois** que a branch
> `feature/campanhas` for mergeada na `main` e o deploy rodar. Antes disso, o site
> funciona normalmente, mas **ainda não é instalável**.
>
> Como conferir se já está no ar: abra https://ebd-ices.duckdns.org/manifest.webmanifest —
> se aparecer o conteúdo JSON (nome do app, cores, ícone), o PWA está publicado.

---

## 2. Como o usuário instala

O que você faz: **manda o link** https://ebd-ices.duckdns.org e (se quiser) estas
instruções. Cada pessoa instala assim:

### 📱 Android (Chrome)
1. Abrir o link no **Chrome**.
2. Tocar no menu **⋮** (canto superior direito).
3. Tocar em **"Instalar aplicativo"** (ou **"Adicionar à tela inicial"**).
4. Confirmar. O ícone do EBD aparece na tela inicial, como um app.

> Muitas vezes o Chrome mostra sozinho um banner **"Instalar"** na parte de baixo.

### 🍎 iPhone / iPad (Safari)
No iOS, a instalação é **só pelo Safari** (o Chrome do iPhone não instala PWA):
1. Abrir o link no **Safari**.
2. Tocar no botão **Compartilhar** (quadrado com seta para cima, embaixo).
3. Rolar e tocar em **"Adicionar à Tela de Início"**.
4. Tocar em **"Adicionar"**. O ícone aparece na tela inicial.

### 💻 Computador (Chrome ou Edge)
1. Abrir o link no **Chrome** ou **Edge**.
2. Na barra de endereço, clicar no ícone de **instalar** (um monitor com seta,
   ou **⋮ → Instalar EBD Adultos**).
3. Confirmar. O app abre em janela própria e pode ser fixado na barra de tarefas.

---

## 3. Login e uso

- Depois de instalado, o app abre **direto no login** — o acesso é o mesmo do site
  (usuário e senha já cadastrados). Cada pessoa usa o seu login/perfil.
- Instalar **não cria conta**: quem faz chamada/administra precisa de um usuário
  criado na tela de **Usuários** (perfil PROFESSOR ou ADMIN).

---

## 4. Atualizações (como o app se mantém novo)

O app usa a estratégia **network-first**: sempre que houver internet, ele **busca a
versão mais recente** do servidor. Ou seja, quando você faz um novo deploy, os
usuários recebem a versão nova **automaticamente** ao abrir o app com internet — não
precisa reinstalar.

- **Offline:** sem internet, o app abre a **última versão** que a pessoa carregou
  (visualização básica). Ações que dependem do servidor (salvar chamada, enviar
  campanha) precisam de conexão.
- **Forçar atualização** (raro, se algo ficar preso): fechar e reabrir o app com
  internet; ou, no navegador, recarregar a página segurando **Shift** (desktop).

---

## 5. Limitações desta 1ª versão do PWA

- **Ícone:** usamos um ícone **SVG** (vetorial) da marca EBD. Funciona bem no Android
  e no desktop; no iOS o ícone pode aparecer mais simples. Para um acabamento perfeito
  no iOS, o próximo passo é gerar ícones **PNG** (192/512 px) — está no roadmap.
- **Offline é básico** (leitura da última tela carregada). Fazer chamada 100% offline
  e sincronizar depois é uma evolução futura (ver `ROADMAP.md`).
- No **iPhone**, a instalação exige o **Safari** (limitação da Apple, não do app).

---

## 6. Mensagem pronta para enviar à classe

> 📖 *Agora a EBD Adultos tem app!*
> Instale no seu celular em 10 segundos, sem baixar da loja:
> 1. Abra **https://ebd-ices.duckdns.org**
>    (no **Android** use o Chrome; no **iPhone** use o Safari)
> 2. **Android:** menu ⋮ → *Instalar aplicativo*.
>    **iPhone:** botão Compartilhar → *Adicionar à Tela de Início*.
> 3. Pronto! O ícone aparece na sua tela inicial. É só entrar com seu login. 🙏
