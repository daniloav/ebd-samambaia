package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.ChamadaResponse;
import br.com.ice.ebd.dto.PresencaItem;
import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ChamadaService {

    private static final Logger LOG = Logger.getLogger(ChamadaService.class);

    /**
     * Máximo de faltas seguidas (sem justificativa) toleradas. Ultrapassá-lo — isto é, a
     * {@value #MAX_FALTAS_SEGUIDAS} + 1-ésima falta consecutiva — inativa o aluno.
     * Uma presença OU uma falta justificada zera a sequência.
     */
    private static final int MAX_FALTAS_SEGUIDAS = 4;

    @Inject EscopoService escopo;

    @Inject AlunoRepository alunoRepository;
    @Inject PresencaRepository presencaRepository;
    @Inject AulaRepository aulaRepository;
    @Inject AulaService aulaService;
    @Inject AuditoriaService auditoria;
    @Inject NotificacaoService notificacaoService;

    /**
     * Monta a chamada de uma aula: todos os alunos ativos, com os itens já
     * registrados (quando existirem) ou zerados (quando ainda não houver registro).
     */
    @Transactional
    public ChamadaResponse obterChamada(Long aulaId) {
        Aula aula = aulaService.obter(aulaId);
        escopo.assertClasse(aula.getClasse().getId());

        // Alunos que estão dando aula neste dia (nesta ou em outra turma) — desabilitados hoje.
        Set<Long> ensinandoHoje = aulaRepository.alunoIdsDeProfessoresComAulaEm(aula.getData());

        Map<Long, Presenca> porAluno = new LinkedHashMap<>();
        for (Presenca p : presencaRepository.listarPorAula(aulaId)) {
            porAluno.put(p.getAluno().getId(), p);
        }

        List<PresencaItem> itens = alunoRepository.listarAtivosPorClasse(aula.getClasse().getId()).stream()
                .map(aluno -> {
                    boolean ehProfessor = ensinandoHoje.contains(aluno.getId());
                    Presenca p = porAluno.get(aluno.getId());
                    if (p == null || ehProfessor) {
                        return new PresencaItem(aluno.getId(), aluno.getNome(),
                                false, false, false, false, ehProfessor);
                    }
                    return new PresencaItem(aluno.getId(), aluno.getNome(),
                            p.isPresente(), p.isTrouxeBiblia(), p.isTrouxeRevista(), p.isEstudouLicao(),
                            false, p.isJustificada(), p.getJustificativaMotivo());
                })
                .toList();

        return new ChamadaResponse(aula.getId(), aula.getData(), aula.getTema(), itens);
    }

    /** Salva (upsert) a chamada da aula, uma linha por aluno. */
    @Transactional
    public ChamadaResponse salvarChamada(Long aulaId, SalvarChamadaRequest req) {
        Aula aula = aulaService.obter(aulaId);
        escopo.assertClasse(aula.getClasse().getId());

        // Quem dá aula neste dia (qualquer turma) não é contabilizado como aluno nesta chamada.
        Set<Long> ensinandoHoje = aulaRepository.alunoIdsDeProfessoresComAulaEm(aula.getData());
        if (!ensinandoHoje.isEmpty()) {
            presencaRepository.delete("aula.id = ?1 and aluno.id in ?2", aulaId, ensinandoHoje);
        }

        Map<Long, Presenca> existentes = new LinkedHashMap<>();
        for (Presenca p : presencaRepository.listarPorAula(aulaId)) {
            existentes.put(p.getAluno().getId(), p);
        }

        List<Long> ausentesIds = new ArrayList<>();
        for (SalvarChamadaRequest.Item item : req.itens()) {
            if (ensinandoHoje.contains(item.alunoId())) {
                continue; // está dando aula neste dia — não registra presença
            }
            Aluno aluno = alunoRepository.findById(item.alunoId());
            if (aluno == null) {
                throw new NotFoundException("Aluno não encontrado: " + item.alunoId());
            }
            Presenca p = existentes.get(item.alunoId());
            if (p == null) {
                p = new Presenca();
                p.setAula(aula);
                p.setAluno(aluno);
            }
            p.setPresente(item.presente());
            p.setTrouxeBiblia(item.trouxeBiblia());
            p.setTrouxeRevista(item.trouxeRevista());
            p.setEstudouLicao(item.estudouLicao());
            // Marca o 1º registro da chamada desta aula (para o "no prazo × atrasada" do painel /uso).
            if (p.getRegistradaEm() == null) {
                p.setRegistradaEm(LocalDateTime.now());
            }
            // A justificativa vem do professor: só vale para quem faltou; presente sempre a limpa.
            if (!item.presente() && item.justificada()) {
                p.setJustificada(true);
                p.setJustificativaMotivo(item.justificativaMotivo() != null
                        ? item.justificativaMotivo().trim() : null);
                if (p.getJustificadaEm() == null) {
                    p.setJustificadaEm(LocalDateTime.now());
                }
            } else {
                p.setJustificada(false);
                p.setJustificativaMotivo(null);
                p.setJustificadaEm(null);
            }
            presencaRepository.persist(p);
            if (!item.presente()) {
                ausentesIds.add(item.alunoId());
            }
        }
        presencaRepository.flush(); // garante que a presença desta aula entra na contagem de sequência

        List<String> alertas = inativarPorFaltasSeguidas(aula, ausentesIds);
        return obterChamada(aulaId).comAlertas(alertas);
    }

    /**
     * Regra de negócio: aluno que ultrapassa {@value #MAX_FALTAS_SEGUIDAS} faltas consecutivas
     * (sem justificativa) fica <b>inativo</b>. Avaliado só para quem faltou nesta aula (só a falta
     * mais recente pode fechar uma sequência). Falta justificada OU presença zera a sequência.
     *
     * @return mensagens de aviso (uma por aluno inativado) para exibir a quem salvou a chamada.
     */
    private List<String> inativarPorFaltasSeguidas(Aula aula, List<Long> ausentesIds) {
        List<String> alertas = new ArrayList<>();
        for (Long alunoId : ausentesIds) {
            Aluno aluno = alunoRepository.findById(alunoId);
            if (aluno == null || !aluno.isAtivo()) {
                continue;
            }
            int seguidas = faltasSeguidasAte(aluno, aula);
            if (seguidas > MAX_FALTAS_SEGUIDAS) {
                aluno.setAtivo(false);
                String msg = String.format("%s foi inativado(a) automaticamente: %d faltas seguidas sem justificativa.",
                        aluno.getNome(), seguidas);
                auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.ALUNO, aluno.getId(), msg);
                notificacaoService.avisarAlunoInativado(aluno, seguidas); // best-effort (respeita toggle/e-mail)
                LOG.info(msg);
                alertas.add(msg);
            }
        }
        return alertas;
    }

    /**
     * Conta as faltas consecutivas (não justificadas) do aluno terminando na aula informada,
     * caminhando das aulas mais recentes para as mais antigas. Para na primeira presença ou
     * falta justificada. Considera só aulas com data ≤ a da aula avaliada.
     */
    private int faltasSeguidasAte(Aluno aluno, Aula aula) {
        int seguidas = 0;
        for (Presenca p : presencaRepository.listarPorAlunoDesc(aluno.getId())) {
            if (p.getAula().getData().isAfter(aula.getData())) {
                continue; // ignora aulas posteriores à avaliada
            }
            if (p.isPresente() || p.isJustificada()) {
                break; // sequência quebrada
            }
            seguidas++;
        }
        return seguidas;
    }
}
