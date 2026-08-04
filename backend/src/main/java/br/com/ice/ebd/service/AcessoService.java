package br.com.ice.ebd.service;

import br.com.ice.ebd.model.AcessoEvento;
import br.com.ice.ebd.model.UsoEvento;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.AcessoEventoRepository;
import br.com.ice.ebd.repository.UsoEventoRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;

/**
 * Registra o uso do app: um evento por login efetivo + o "último acesso" (last-seen) do
 * usuário. O last-seen é atualizado tanto no login quanto no ping periódico do front,
 * dando base ao "online agora" do painel /uso. Falha aqui nunca deve derrubar o login,
 * então o registro roda em transação própria e o chamador engole exceções.
 */
@ApplicationScoped
public class AcessoService {

    @Inject UsuarioRepository usuarioRepository;
    @Inject AcessoEventoRepository eventoRepository;
    @Inject UsoEventoRepository usoEventoRepository;

    /** Grava o login e atualiza o last-seen. Chamado após autenticação bem-sucedida. */
    @Transactional
    public void registrarLogin(Long usuarioId, String userAgent) {
        Usuario u = usuarioRepository.findById(usuarioId);
        if (u == null) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        u.setUltimoAcesso(agora);
        AcessoEvento e = new AcessoEvento();
        e.setUsuario(u);
        e.setDataHora(agora);
        e.setUserAgent(truncar(userAgent));
        eventoRepository.persist(e);
    }

    /**
     * Registra o uso de uma funcionalidade (page view ou clique notável) para o item D do
     * painel /uso. Falha aqui nunca deve atrapalhar a navegação, então roda em transação
     * própria e o chamador engole exceções. {@code recurso} inválido/vazio é ignorado.
     */
    @Transactional
    public void registrarEvento(String username, String recurso, UsoEvento.Acao acao) {
        String r = normalizarRecurso(recurso);
        if (r == null) {
            return;
        }
        usuarioRepository.findByUsername(username).ifPresent(u -> {
            UsoEvento e = new UsoEvento();
            e.setUsuario(u);
            e.setDataHora(LocalDateTime.now());
            e.setRecurso(r);
            e.setAcao(acao == null ? UsoEvento.Acao.ABRIR : acao);
            usoEventoRepository.persist(e);
        });
    }

    /** Normaliza a chave do recurso (minúsculas, sem espaços nas pontas, no máx. 60 chars). */
    private static String normalizarRecurso(String recurso) {
        if (recurso == null) {
            return null;
        }
        String r = recurso.trim().toLowerCase();
        if (r.isEmpty()) {
            return null;
        }
        return r.length() > 60 ? r.substring(0, 60) : r;
    }

    /** Heartbeat leve: só atualiza o last-seen (sem gerar evento). Chamado pelo ping do app. */
    @Transactional
    public void ping(String username) {
        usuarioRepository.findByUsername(username)
                .ifPresent(u -> u.setUltimoAcesso(LocalDateTime.now()));
    }

    private static String truncar(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
