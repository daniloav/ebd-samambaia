package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.AlunoRequest;
import br.com.ice.ebd.dto.AlunoResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.model.MotivoInativacao;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AlunoService {

    @Inject EscopoService escopo;

    @Inject
    AlunoRepository repository;

    @Inject
    ClasseService classeService;

    @Inject
    AcessoAlunoService acessoAluno;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    AuditoriaService auditoria;

    @Inject
    InativacaoService inativacao;

    public List<AlunoResponse> listar(Long classeId, boolean apenasAtivos) {
        escopo.assertClasse(classeId);
        List<Aluno> alunos;
        if (classeId != null) {
            alunos = apenasAtivos ? repository.listarAtivosPorClasse(classeId)
                                  : repository.listarPorClasse(classeId);
        } else {
            alunos = apenasAtivos ? repository.listarAtivos() : repository.listarOrdenadoPorNome();
        }
        Map<Long, String> logins = usuarioRepository.loginsPorAluno();
        return alunos.stream().map(a -> AlunoResponse.de(a, logins.get(a.getId()))).toList();
    }

    public AlunoResponse buscar(Long id) {
        Aluno a = obter(id);
        escopo.assertClasse(a.getClasse().getId());
        return AlunoResponse.de(a, usuarioRepository.loginDoAluno(a.getId()));
    }

    @Transactional
    public AlunoResponse criar(AlunoRequest req) {
        Aluno a = new Aluno();
        aplicar(a, req);
        repository.persist(a);
        repository.flush();               // garante o id antes de vincular o login
        acessoAluno.sincronizarAcesso(a); // todo aluno cadastrado ganha acesso (senha padrão + troca no 1º login)
        if (!a.isAtivo()) {
            inativacao.registrarInativacao(a, MotivoInativacao.MANUAL, null); // cadastrado já inativo
        }
        if (req.login() != null && !req.login().isBlank()) {
            acessoAluno.definirLogin(a.getId(), req.login()); // login customizado (senão fica o automático)
        }
        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.ALUNO, a.getId(), a.getNome());
        return AlunoResponse.de(a, usuarioRepository.loginDoAluno(a.getId()));
    }

    @Transactional
    public AlunoResponse atualizar(Long id, AlunoRequest req) {
        Aluno a = obter(id);
        escopo.assertClasse(a.getClasse().getId());
        boolean estavaAtivo = a.isAtivo();
        aplicar(a, req);
        // Marcar/desmarcar "ativo" no cadastro abre ou fecha um episódio no histórico de inativação.
        if (estavaAtivo && !a.isAtivo()) {
            inativacao.registrarInativacao(a, MotivoInativacao.MANUAL, null);
        } else if (!estavaAtivo && a.isAtivo()) {
            inativacao.registrarReativacao(a);
        }
        acessoAluno.sincronizarAcesso(a);
        if (req.login() != null && !req.login().isBlank()) {
            acessoAluno.definirLogin(a.getId(), req.login()); // troca o login se informado (e diferente)
        }
        auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.ALUNO, a.getId(), a.getNome());
        return AlunoResponse.de(a, usuarioRepository.loginDoAluno(a.getId()));
    }

    @Transactional
    public void deletar(Long id) {
        Aluno a = obter(id);
        auditoria.registrar(AcaoAuditoria.EXCLUIR, EntidadeAuditoria.ALUNO, a.getId(), a.getNome());
        acessoAluno.removerAcesso(a.getId()); // remove o login vinculado (FK)
        repository.delete(a);
    }

    private Aluno obter(Long id) {
        Aluno a = repository.findById(id);
        if (a == null) {
            throw new NotFoundException("Aluno não encontrado: " + id);
        }
        return a;
    }

    private void aplicar(Aluno a, AlunoRequest req) {
        escopo.assertClasse(req.classeId());
        a.setNome(req.nome().trim());
        a.setTelefone(req.telefone());
        a.setDataNascimento(req.dataNascimento());
        a.setAtivo(req.ativo() == null ? true : req.ativo());
        a.setClasse(classeService.obter(req.classeId()));
        a.setEmail(req.email() != null && !req.email().isBlank() ? req.email().trim() : null);
        a.setRecebeNotificacoes(req.recebeNotificacoes() != null && req.recebeNotificacoes());
    }
}
