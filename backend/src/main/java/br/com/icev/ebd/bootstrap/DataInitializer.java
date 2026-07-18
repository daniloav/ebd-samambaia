package br.com.icev.ebd.bootstrap;

import br.com.icev.ebd.model.Aluno;
import br.com.icev.ebd.model.Role;
import br.com.icev.ebd.model.Usuario;
import br.com.icev.ebd.repository.AlunoRepository;
import br.com.icev.ebd.repository.UsuarioRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Cria os usuários padrão e alguns alunos de exemplo no primeiro boot. */
@ApplicationScoped
public class DataInitializer {

    private static final Logger LOG = Logger.getLogger(DataInitializer.class);

    @Inject UsuarioRepository usuarioRepository;
    @Inject AlunoRepository alunoRepository;

    @ConfigProperty(name = "ebd.seed.admin.username") String adminUser;
    @ConfigProperty(name = "ebd.seed.admin.password") String adminPass;
    @ConfigProperty(name = "ebd.seed.professor.username") String profUser;
    @ConfigProperty(name = "ebd.seed.professor.password") String profPass;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        seedUsuarios();
        seedAlunosExemplo();
    }

    private void seedUsuarios() {
        if (usuarioRepository.count() > 0) {
            return;
        }
        criarUsuario(adminUser, adminPass, Role.ADMIN);
        criarUsuario(profUser, profPass, Role.PROFESSOR);
        LOG.infof("Usuários padrão criados: admin='%s', professor='%s' (troque as senhas!)",
                adminUser, profUser);
    }

    private void criarUsuario(String username, String senha, Role role) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setSenhaHash(BcryptUtil.bcryptHash(senha));
        u.setRole(role);
        u.setAtivo(true);
        usuarioRepository.persist(u);
    }

    private void seedAlunosExemplo() {
        if (alunoRepository.count() > 0) {
            return;
        }
        List<String> nomes = List.of(
                "Ana Beatriz Souza", "Carlos Eduardo Lima", "Débora Martins",
                "Fernando Alves", "Joana Ribeiro", "Marcos Vinícius Silva");
        for (String nome : nomes) {
            Aluno a = new Aluno();
            a.setNome(nome);
            a.setAtivo(true);
            alunoRepository.persist(a);
        }
        LOG.infof("%d alunos de exemplo criados.", nomes.size());
    }
}
