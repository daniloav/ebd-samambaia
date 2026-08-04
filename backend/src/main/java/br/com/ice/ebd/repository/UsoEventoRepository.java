package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.UsoEvento;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UsoEventoRepository implements PanacheRepository<UsoEvento> {
}
