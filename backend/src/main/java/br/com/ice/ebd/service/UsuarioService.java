package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.UsuarioRequest;
import br.com.ice.ebd.dto.UsuarioResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.ClasseRepository;
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

    @Inject UsuarioRepository repository;
    @Inject AlunoRepository alunoRepository;
    @Inject ClasseRepository classeRepository;

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
        validarUsernameUnico(req.username(), null);
        Usuario u = new Usuario();
        u.setUsername(req.username().trim());
        u.setSenhaHash(BcryptUtil.bcryptHash(req.senha()));
        aplicarComuns(u, req);
        repository.persist(u);
        return UsuarioResponse.de(u);
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest req) {
        Usuario u = obter(id);
        validarUsernameUnico(req.username(), id);
        u.setUsername(req.username().trim());
        if (req.senha() != null && !req.senha().isBlank()) {
            u.setSenhaHash(BcryptUtil.bcryptHash(req.senha()));
        }
        aplicarComuns(u, req);
        return UsuarioResponse.de(u);
    }

    @Transactional
    public void deletar(Long id) {
        Usuario u = obter(id);
        if (u.getRole() == Role.ADMIN && contarAdmins() <= 1) {
            throw new WebApplicationException("Não é possível excluir o último administrador.",
                    Response.Status.CONFLICT);
        }
        repository.delete(u);
    }

    private void aplicarComuns(Usuario u, UsuarioRequest req) {
        u.setRole(req.role());
        u.setAtivo(req.ativo() == null ? true : req.ativo());

        // Vínculo com aluno: só faz sentido para a role ALUNO.
        if (req.role() == Role.ALUNO && req.alunoId() != null) {
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
        if (req.role() == Role.PROFESSOR && req.classeIds() != null) {
            for (Long cid : req.classeIds()) {
                Classe c = classeRepository.findById(cid);
                if (c == null) {
                    throw new NotFoundException("Classe não encontrada: " + cid);
                }
                u.getClasses().add(c);
            }
        }
    }

    private Usuario obter(Long id) {
        Usuario u = repository.findById(id);
        if (u == null) {
            throw new NotFoundException("Usuário não encontrado: " + id);
        }
        return u;
    }

    private void validarUsernameUnico(String username, Long idAtual) {
        Optional<Usuario> existente = repository.findByUsername(username.trim());
        if (existente.isPresent() && !existente.get().getId().equals(idAtual)) {
            throw new WebApplicationException("Já existe um usuário com este nome.",
                    Response.Status.CONFLICT);
        }
    }

    private long contarAdmins() {
        return repository.count("role", Role.ADMIN);
    }
}
