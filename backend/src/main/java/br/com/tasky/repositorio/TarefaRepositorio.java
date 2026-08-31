package br.com.tasky.repositorio;

import br.com.tasky.dominio.Tarefa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TarefaRepositorio extends JpaRepository<Tarefa, Long> {
}
