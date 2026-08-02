package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.LoginRequest;
import br.com.ice.ebd.dto.LoginResponse;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.UsuarioRepository;
import br.com.ice.ebd.security.TokenService;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import java.util.Optional;

@ApplicationScoped
public class AuthService {

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    TokenService tokenService;

    @Inject
    ProtecaoLoginService protecao;

    @Inject
    AcessoService acesso;

    public LoginResponse autenticar(LoginRequest req, String userAgent) {
        protecao.verificarBloqueio(req.username());
        Optional<Usuario> opt = usuarioRepository.findByUsername(req.username());
        if (opt.isEmpty()) {
            protecao.registrarFalha(req.username());
            throw new NotAuthorizedException("Usuário ou senha inválidos", "Bearer");
        }
        Usuario usuario = opt.get();
        if (!usuario.isAtivo() || !BcryptUtil.matches(req.senha(), usuario.getSenhaHash())) {
            protecao.registrarFalha(req.username());
            throw new NotAuthorizedException("Usuário ou senha inválidos", "Bearer");
        }
        protecao.registrarSucesso(req.username());
        try {
            acesso.registrarLogin(usuario.getId(), userAgent);
        } catch (RuntimeException e) {
            // Estatística de uso nunca deve derrubar o login.
        }
        String token = tokenService.gerarToken(usuario);
        boolean admin = usuario.isEhAdmin();
        return new LoginResponse(token, usuario.getUsername(),
                tokenService.getDurationSeconds(), usuario.isPrecisaTrocarSenha(),
                admin, usuario.isEhProfessor(), usuario.isEhAluno(),
                admin || usuario.isEhTesoureiro(), admin || usuario.isEhLider());
    }
}
