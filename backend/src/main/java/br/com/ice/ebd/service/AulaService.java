package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.AulaComplementarRequest;
import br.com.ice.ebd.dto.AulaAdiarResponse;
import br.com.ice.ebd.dto.AulaComplementarResponse;
import br.com.ice.ebd.dto.AulaRequest;
import br.com.ice.ebd.dto.AulaResponse;
import br.com.ice.ebd.dto.TextoBiblicoRequest;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.DiaSemanaLeitura;
import br.com.ice.ebd.model.TextoBiblicoAula;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.UsuarioRepository;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.repository.AulaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AulaService {

    @Inject EscopoService escopo;
    @Inject AuditoriaService auditoria;

    @Inject
    AulaRepository repository;

    @Inject
    ClasseService classeService;

    @Inject
    UsuarioRepository usuarioRepository;

    @Transactional
    public List<AulaResponse> listar(Long classeId) {
        escopo.assertClasse(classeId);
        var aulas = classeId != null ? repository.listarPorClasse(classeId) : repository.listarOrdenadoPorData();
        return aulas.stream().map(AulaResponse::de).toList();
    }

    @Transactional
    public AulaResponse buscar(Long id) {
        Aula a = obter(id);
        escopo.assertClasse(a.getClasse().getId());
        return AulaResponse.de(a);
    }

    @Transactional
    public AulaResponse criar(AulaRequest req) {
        escopo.assertClasse(req.classeId());
        var classe = classeService.obter(req.classeId());
        validarDataUnica(classe.getId(), req.data(), null);
        Aula a = new Aula();
        a.setClasse(classe);
        a.setData(req.data());
        a.setTema(req.tema());
        a.setProfessor(resolverProfessor(req.professorId()));
        repository.persist(a);
        sincronizarTextos(a, req.textos());
        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.AULA, a.getId(), rotulo(a));
        return AulaResponse.de(a);
    }

    @Transactional
    public AulaResponse atualizar(Long id, AulaRequest req) {
        escopo.assertClasse(req.classeId());
        Aula a = obter(id);
        var classe = classeService.obter(req.classeId());
        validarDataUnica(classe.getId(), req.data(), id);
        a.setClasse(classe);
        a.setData(req.data());
        a.setTema(req.tema());
        a.setProfessor(resolverProfessor(req.professorId()));
        sincronizarTextos(a, req.textos());
        auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.AULA, a.getId(), rotulo(a));
        return AulaResponse.de(a);
    }

    @Transactional
    public void deletar(Long id) {
        Aula a = obter(id);
        auditoria.registrar(AcaoAuditoria.EXCLUIR, EntidadeAuditoria.AULA, a.getId(), rotulo(a));
        repository.delete(a); // presenças são removidas em cascata (FK ON DELETE CASCADE)
    }

    /**
     * Desdobra uma aula: cria a <b>continuação no próximo domingo</b> (origem + 7 dias) e
     * <b>empurra +7 dias toda a agenda seguinte</b> da mesma turma, preservando tema e professor
     * de cada aula. O empurrão é feito da aula mais recente para a mais antiga, com flush por
     * iteração, para nunca colidir com a unique {@code uq_aula_classe_data} (não-deferrable):
     * a mais recente vai para o slot vazio e cada aula anterior ocupa o slot recém-liberado.
     */
    @Transactional
    public AulaComplementarResponse complementar(Long origemId, AulaComplementarRequest req) {
        Aula origem = obter(origemId);
        Long classeId = origem.getClasse().getId();
        escopo.assertClasse(classeId);

        LocalDate novaData = origem.getData().plusDays(7);

        // Empurra a agenda seguinte (+7d) para abrir o domingo recém-liberado.
        int movidas = empurrarAgenda(classeId, novaData);

        // Cria a aula complementar no domingo recém-liberado.
        Aula nova = new Aula();
        nova.setClasse(origem.getClasse());
        nova.setData(novaData);
        nova.setTema(temaComplemento(req.tema(), origem.getTema()));
        nova.setProfessor(req.professorId() != null ? resolverProfessor(req.professorId()) : origem.getProfessor());
        validarDataUnica(classeId, novaData, null); // sanidade: já deve estar livre após o empurrão
        repository.persist(nova);

        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.AULA, nova.getId(),
                "complementar de " + origem.getData() + " · " + rotulo(nova));
        if (movidas > 0) {
            auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.AULA, origem.getId(),
                    "empurrão +7d na agenda da turma: " + movidas + " aula(s)");
        }
        return new AulaComplementarResponse(AulaResponse.de(nova), movidas);
    }

    /**
     * Adia (cancela) uma aula: marca a origem como <b>adiada</b> — a partir daí ela é ignorada por
     * toda pontuação e retrospecto (chamada, rankings, relatórios, boletim, dashboard, frequência,
     * inativação por faltas e promoção de visitante), então ninguém é penalizado por ela — e
     * <b>empurra +7 dias a agenda seguinte</b> da turma, criando uma aula de <b>reposição</b> no
     * domingo recém-liberado (herda tema e professor da origem), para a lição não se perder. Usa a
     * mesma mecânica de empurrão do desdobramento (DESC + flush por item, sem violar a unique
     * {@code uq_aula_classe_data}).
     */
    @Transactional
    public AulaAdiarResponse adiar(Long id) {
        Aula origem = obter(id);
        Long classeId = origem.getClasse().getId();
        escopo.assertClasse(classeId);
        if (origem.isAdiada()) {
            throw new WebApplicationException("Esta aula já está adiada.", Response.Status.CONFLICT);
        }

        LocalDate novaData = origem.getData().plusDays(7);

        // Desabilita a pontuação/retrospecto da aula cancelada (fica no lugar, marcada).
        origem.setAdiada(true);

        // Empurra a agenda seguinte (+7d) e cria a reposição no domingo recém-liberado.
        int movidas = empurrarAgenda(classeId, novaData);

        Aula reposicao = new Aula();
        reposicao.setClasse(origem.getClasse());
        reposicao.setData(novaData);
        reposicao.setTema(origem.getTema());
        reposicao.setProfessor(origem.getProfessor());
        validarDataUnica(classeId, novaData, null); // sanidade: já deve estar livre após o empurrão
        repository.persist(reposicao);
        copiarTextos(origem, reposicao); // a lição foi só remarcada: as leituras diárias vão junto

        auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.AULA, origem.getId(),
                "aula adiada (pontuação desabilitada) · " + rotulo(origem));
        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.AULA, reposicao.getId(),
                "reposição de aula adiada de " + origem.getData() + " · " + rotulo(reposicao));
        if (movidas > 0) {
            auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.AULA, origem.getId(),
                    "empurrão +7d na agenda da turma: " + movidas + " aula(s)");
        }
        return new AulaAdiarResponse(AulaResponse.de(origem), AulaResponse.de(reposicao), movidas);
    }

    /**
     * Empurra +7 dias todas as aulas da turma com data &gt;= {@code aPartir}, da mais recente para
     * a mais antiga com flush por iteração — a mais recente vai ao slot vazio e cada anterior ocupa
     * o recém-liberado, sem nunca violar a unique {@code uq_aula_classe_data} (não-deferrable).
     *
     * @return quantas aulas foram movidas.
     */
    private int empurrarAgenda(Long classeId, LocalDate aPartir) {
        var seguintes = repository.listarPorClasseDesde(classeId, aPartir);
        for (Aula a : seguintes) {
            a.setData(a.getData().plusDays(7));
            repository.getEntityManager().flush();
        }
        return seguintes.size();
    }

    /**
     * Sincroniza as leituras bíblicas diárias da aula com o que veio na requisição. A lista é a
     * íntegra do cadastro: dia ausente (ou com referência em branco) é removido, dia novo é
     * criado e referência alterada zera o cache do texto e o carimbo de envio — a leitura antiga
     * já não vale. Lista nula significa "não mexer" (cliente que não conhece o campo).
     */
    private void sincronizarTextos(Aula a, List<TextoBiblicoRequest> textos) {
        if (textos == null) {
            return;
        }
        Map<DiaSemanaLeitura, String> desejados = new EnumMap<>(DiaSemanaLeitura.class);
        for (TextoBiblicoRequest t : textos) {
            if (t == null || t.diaSemana() == null || t.referencia() == null || t.referencia().isBlank()) {
                continue;
            }
            desejados.put(t.diaSemana(), t.referencia().trim());
        }
        a.getTextos().removeIf(existente -> !desejados.containsKey(existente.getDiaSemana()));
        for (Map.Entry<DiaSemanaLeitura, String> e : desejados.entrySet()) {
            TextoBiblicoAula atual = a.getTextos().stream()
                    .filter(x -> x.getDiaSemana() == e.getKey())
                    .findFirst().orElse(null);
            if (atual == null) {
                TextoBiblicoAula novo = new TextoBiblicoAula();
                novo.setAula(a);
                novo.setDiaSemana(e.getKey());
                novo.setReferencia(e.getValue());
                a.getTextos().add(novo);
            } else if (!e.getValue().equals(atual.getReferencia())) {
                atual.setReferencia(e.getValue());
                atual.setTextoCache(null);
                atual.setTextoCacheEm(null);
                atual.setEnviadoEm(null);
            }
        }
        repository.getEntityManager().flush(); // as leituras novas já saem com id na resposta
    }

    /** Copia as leituras diárias da origem para a nova aula (envio zerado: as datas mudaram). */
    private void copiarTextos(Aula origem, Aula destino) {
        for (TextoBiblicoAula t : origem.getTextos()) {
            TextoBiblicoAula copia = new TextoBiblicoAula();
            copia.setAula(destino);
            copia.setDiaSemana(t.getDiaSemana());
            copia.setReferencia(t.getReferencia());
            copia.setTextoCache(t.getTextoCache());
            copia.setTextoCacheEm(t.getTextoCacheEm());
            destino.getTextos().add(copia);
        }
    }

    /** Tema informado, ou o da origem com sufixo "(continuação)". */
    private static String temaComplemento(String temaInformado, String temaOrigem) {
        if (temaInformado != null && !temaInformado.isBlank()) {
            return temaInformado;
        }
        if (temaOrigem == null || temaOrigem.isBlank()) {
            return null;
        }
        return temaOrigem + " (continuação)";
    }

    /** Resolve o professor (usuário PROFESSOR) do id, ou null. Valida o perfil. */
    private Usuario resolverProfessor(Long professorId) {
        if (professorId == null) {
            return null;
        }
        Usuario u = usuarioRepository.findById(professorId);
        if (u == null || !u.isEhProfessor()) {
            throw new WebApplicationException("Professor inválido para a aula.", Response.Status.BAD_REQUEST);
        }
        return u;
    }

    public Aula obter(Long id) {
        Aula a = repository.findById(id);
        if (a == null) {
            throw new NotFoundException("Aula não encontrada: " + id);
        }
        return a;
    }

    private void validarDataUnica(Long classeId, java.time.LocalDate data, Long idAtual) {
        Optional<Aula> existente = repository.findByClasseAndData(classeId, data);
        if (existente.isPresent() && !existente.get().getId().equals(idAtual)) {
            throw new WebApplicationException("Já existe uma aula desta classe nesta data.",
                    Response.Status.CONFLICT);
        }
    }

    private static String rotulo(br.com.ice.ebd.model.Aula a) {
        String tema = a.getTema() == null ? "" : a.getTema();
        return a.getData() + (tema.isBlank() ? "" : " · " + tema);
    }
}
