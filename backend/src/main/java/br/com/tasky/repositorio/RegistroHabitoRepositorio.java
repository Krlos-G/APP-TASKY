package br.com.tasky.repositorio;

import br.com.tasky.dominio.RegistroHabito;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroHabitoRepositorio extends JpaRepository<RegistroHabito, Long> {
}
