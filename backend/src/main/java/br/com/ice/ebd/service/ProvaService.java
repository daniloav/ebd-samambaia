package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.NotaItem;
import br.com.ice.ebd.dto.NotasProvaResponse;
import br.com.ice.ebd.dto.ProvaRequest;
import br.com.ice.ebd.dto.ProvaResponse;
import br.com.ice.ebd.dto.SalvarNotasRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.NotaProva;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.NotaProvaRepository;
import br.com.ice.ebd.repository.ProvaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ProvaService {

    @Inject EscopoService escopo;

    @Inject ProvaRepository provaRepository;
    @Inject ClasseService classeService;
    @Inject NotaProvaRepository notaRepository;
    @Inject AlunoRepository alunoRepository;

    // ---------- CRUD da prova ----------

    public List<ProvaResponse> listar(Long classeId) {
        escopo.assertClasse(classeId);
        var provas = classeId != null ? provaRepository.listarPorClasse(classeId)
                                       : provaRepository.listarOrdenadoPorData();
        return provas.stream().map(ProvaResponse::de).toList();
    }

    public ProvaResponse buscar(Long id) {
        Prova p = obter(id);
        escopo.assertClasse(p.getClasse().getId());
        return ProvaResponse.de(p);
    }

    @Transactional
    public ProvaResponse criar(ProvaRequest req) {
        escopo.assertClasse(req.classeId());
        Prova p = new Prova();
        aplicar(p, req);
        provaRepository.persist(p);
        return ProvaResponse.de(p);
    }

    @Transactional
    public ProvaResponse atualizar(Long id, ProvaRequest req) {
        escopo.assertClasse(req.classeId());
        Prova p = obter(id);
        aplicar(p, req);
        return ProvaResponse.de(p);
    }

    @Transactional
    public void deletar(Long id) {
        Prova p = obter(id);
        provaRepository.delete(p); // notas removidas em cascata
    }

    // ---------- Notas ----------

    /** Grade de notas: todos os alunos ativos, com a nota lançada (ou null). */
    public NotasProvaResponse obterNotas(Long provaId) {
        Prova prova = obter(provaId);
        escopo.assertClasse(prova.getClasse().getId());
        Map<Long, BigDecimal> notasPorAluno = new LinkedHashMap<>();
        for (NotaProva n : notaRepository.listarPorProva(provaId)) {
            notasPorAluno.put(n.getAluno().getId(), n.getNota());
        }
        List<NotaItem> itens = alunoRepository.listarAtivosPorClasse(prova.getClasse().getId()).stream()
                .map(a -> new NotaItem(a.getId(), a.getNome(), notasPorAluno.get(a.getId())))
                .toList();
        return new NotasProvaResponse(prova.getId(), prova.getTitulo(), prova.getData(),
                prova.getNotaMaxima(), itens);
    }

    @Transactional
    public NotasProvaResponse salvarNotas(Long provaId, SalvarNotasRequest req) {
        Prova prova = obter(provaId);
        escopo.assertClasse(prova.getClasse().getId());

        Map<Long, NotaProva> existentes = new LinkedHashMap<>();
        for (NotaProva n : notaRepository.listarPorProva(provaId)) {
            existentes.put(n.getAluno().getId(), n);
        }

        for (SalvarNotasRequest.Item item : req.itens()) {
            if (item.nota() == null) {
                // nota em branco: remove registro existente, se houver
                NotaProva existente = existentes.get(item.alunoId());
                if (existente != null) {
                    notaRepository.delete(existente);
                }
                continue;
            }
            if (item.nota().compareTo(prova.getNotaMaxima()) > 0) {
                throw new WebApplicationException(
                        "A nota do aluno " + item.alunoId() + " excede a nota máxima da prova ("
                                + prova.getNotaMaxima() + ").",
                        Response.Status.BAD_REQUEST);
            }
            Aluno aluno = alunoRepository.findById(item.alunoId());
            if (aluno == null) {
                throw new NotFoundException("Aluno não encontrado: " + item.alunoId());
            }
            NotaProva n = existentes.get(item.alunoId());
            if (n == null) {
                n = new NotaProva();
                n.setProva(prova);
                n.setAluno(aluno);
            }
            n.setNota(item.nota());
            notaRepository.persist(n);
        }
        return obterNotas(provaId);
    }

    // ---------- helpers ----------

    private Prova obter(Long id) {
        Prova p = provaRepository.findById(id);
        if (p == null) {
            throw new NotFoundException("Prova não encontrada: " + id);
        }
        return p;
    }

    private void aplicar(Prova p, ProvaRequest req) {
        p.setClasse(classeService.obter(req.classeId()));
        p.setTitulo(req.titulo().trim());
        p.setData(req.data());
        p.setNotaMaxima(req.notaMaxima());
    }
}
