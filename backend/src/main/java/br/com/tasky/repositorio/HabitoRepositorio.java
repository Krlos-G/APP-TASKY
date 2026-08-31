package br.com.tasky.repositorio;

import br.com.tasky.dominio.Habito;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitoRepositorio extends JpaRepository<Habito, Long> {
}
