package br.com.icev.ebd.service;

import br.com.icev.ebd.dto.LoginRequest;
import br.com.icev.ebd.dto.LoginResponse;
import br.com.icev.ebd.model.Usuario;
import br.com.icev.ebd.repository.UsuarioRepository;
import br.com.icev.ebd.security.TokenService;
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

    public LoginResponse autenticar(LoginRequest req) {
        Optional<Usuario> opt = usuarioRepository.findByUsername(req.username());
        if (opt.isEmpty()) {
            throw new NotAuthorizedException("Usuário ou senha inválidos", "Bearer");
        }
        Usuario usuario = opt.get();
        if (!usuario.isAtivo() || !BcryptUtil.matches(req.senha(), usuario.getSenhaHash())) {
            throw new NotAuthorizedException("Usuário ou senha inválidos", "Bearer");
        }
        String token = tokenService.gerarToken(usuario);
        return new LoginResponse(token, usuario.getUsername(), usuario.getRole().name(),
                tokenService.getDurationSeconds());
    }
}
