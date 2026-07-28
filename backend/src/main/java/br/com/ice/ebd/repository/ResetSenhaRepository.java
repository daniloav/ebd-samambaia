package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.ResetSenha;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class ResetSenhaRepository implements PanacheRepository<ResetSenha> {
    public Optional<ResetSenha> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }
}
