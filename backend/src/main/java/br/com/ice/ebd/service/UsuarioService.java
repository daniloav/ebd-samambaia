package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.TrocarSenhaRequest;
import br.com.ice.ebd.dto.UsuarioRequest;
import br.com.ice.ebd.dto.UsuarioResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.ClasseRepository;
import br.com.ice.ebd.repository.RequisicaoRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UsuarioService {

    /** Tamanho mínimo de senha exigido em toda a aplicação. */
    public static final int SENHA_MIN = 8;

    @Inject UsuarioRepository repository;
    @Inject AuditoriaService auditoria;
    @Inject LoginService loginService;
    @Inject AlunoRepository alunoRepository;
    @Inject ClasseRepository classeRepository;
    @Inject RequisicaoRepository requisicaoRepository;

    public List<UsuarioResponse> listar() {
        return repository.listarOrdenado().stream().map(UsuarioResponse::de).toList();
    }

    public UsuarioResponse buscar(Long id) {
        return UsuarioResponse.de(obter(id));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest req) {
        if (req.senha() == null || req.senha().isBlank()) {
            throw new WebApplicationException("A senha é obrigatória para criar um usuário.",
                    Response.Status.BAD_REQUEST);
        }
        validarForcaSenha(req.senha());
        Usuario u = new Usuario();
        u.setUsername(loginService.preparar(req.username(), null));
        u.setSenhaHash(BcryptUtil.bcryptHash(req.senha()));
        aplicarComuns(u, req);
        repository.persist(u);
        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.USUARIO, u.getId(), u.getUsername());
        return UsuarioResponse.de(u);
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest req) {
        Usuario u = obter(id);
        if (!loginService.normalizar(req.username()).equals(u.getUsername())) {
            u.setUsername(loginService.preparar(req.username(), id));
        }
        if (req.senha() != null && !req.senha().isBlank()) {
            validarForcaSenha(req.senha());
            u.setSenhaHash(BcryptUtil.bcryptHash(req.senha()));
        }
        aplicarComuns(u, req);
        auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.USUARIO, u.getId(), u.getUsername());
        return UsuarioResponse.de(u);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario u = obter(id);
        if (u.isEhAdmin() && contarAdmins() <= 1) {
            throw new WebApplicationException("Não é possível excluir o último administrador.",
                    Response.Status.CONFLICT);
        }
        long reqs = requisicaoRepository.contarComoSolicitante(id);
        if (reqs > 0) {
            throw new WebApplicationException(
                    "Não é possível excluir: este usuário abriu " + reqs + " requisição(ões) de tesouraria. "
                            + "Reatribua ou exclua essas requisições antes.", Response.Status.CONFLICT);
        }
        auditoria.registrar(AcaoAuditoria.EXCLUIR, EntidadeAuditoria.USUARIO, u.getId(), u.getUsername());
        repository.delete(u);
    }

    private void aplicarComuns(Usuario u, UsuarioRequest req) {
        u.setEhAdmin(Boolean.TRUE.equals(req.ehAdmin()));
        u.setEhProfessor(Boolean.TRUE.equals(req.ehProfessor()));
        u.setEhAluno(Boolean.TRUE.equals(req.ehAluno()));
        u.setAtivo(req.ativo() == null ? true : req.ativo());
        u.setEmail(req.email() != null && !req.email().isBlank() ? req.email().trim() : null);
        u.setEhTesoureiro(Boolean.TRUE.equals(req.ehTesoureiro()));
        u.setEhLider(Boolean.TRUE.equals(req.ehLider()));

        // Vínculo com aluno: para ALUNO (visão própria) e para PROFESSOR (aluno correlato que
        // fica desabilitado nas aulas que ele dá).
        if ((Boolean.TRUE.equals(req.ehAluno()) || Boolean.TRUE.equals(req.ehProfessor())) && req.alunoId() != null) {
            Aluno aluno = alunoRepository.findById(req.alunoId());
            if (aluno == null) {
                throw new NotFoundException("Aluno não encontrado: " + req.alunoId());
            }
            u.setAluno(aluno);
        } else {
            u.setAluno(null);
        }

        // Turmas vinculadas: só para PROFESSOR.
        u.getClasses().clear();
        if (Boolean.TRUE.equals(req.ehProfessor()) && req.classeIds() != null) {
            for (Long cid : req.classeIds()) {
                Classe c = classeRepository.findById(cid);
                if (c == null) {
                    throw new NotFoundException("Classe não encontrada: " + cid);
                }
                u.getClasses().add(c);
            }
        }
    }

    /**
     * Troca da própria senha pelo usuário autenticado: confere a senha atual,
     * exige força mínima e recusa repetir a senha vigente.
     */
    @Transactional
    public void trocarPropriaSenha(String username, TrocarSenhaRequest req) {
        Usuario u = repository.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        if (req == null || req.senhaAtual() == null
                || !BcryptUtil.matches(req.senhaAtual(), u.getSenhaHash())) {
            throw new WebApplicationException("Senha atual incorreta.", Response.Status.BAD_REQUEST);
        }
        validarForcaSenha(req.novaSenha());
        if (BcryptUtil.matches(req.novaSenha(), u.getSenhaHash())) {
            throw new WebApplicationException("A nova senha deve ser diferente da atual.",
                    Response.Status.BAD_REQUEST);
        }
        u.setSenhaHash(BcryptUtil.bcryptHash(req.novaSenha()));
        u.setPrecisaTrocarSenha(false); // 1º acesso concluído
    }

    /** Exige senha com pelo menos {@link #SENHA_MIN} caracteres. */
    private void validarForcaSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MIN) {
            throw new WebApplicationException(
                    "A senha deve ter pelo menos " + SENHA_MIN + " caracteres.",
                    Response.Status.BAD_REQUEST);
        }
    }

    private Usuario obter(Long id) {
        Usuario u = repository.findById(id);
        if (u == null) {
            throw new NotFoundException("Usuário não encontrado: " + id);
        }
        return u;
    }

    private long contarAdmins() {
        return repository.count("ehAdmin", true);
    }
}
