package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.AcessoEvento;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AcessoEventoRepository implements PanacheRepository<AcessoEvento> {
}
