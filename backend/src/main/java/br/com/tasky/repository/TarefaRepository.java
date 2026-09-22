package br.com.tasky.repository;

import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.enums.StatusTarefa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TarefaRepository extends JpaRepository<Tarefa, Long> {

    Optional<Tarefa> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Tarefa> findByUsuarioIdAndDataPlanejadaAndStatusNot(
            Long usuarioId, LocalDate data, StatusTarefa excluido);

    List<Tarefa> findByUsuarioIdAndStatusAndDataPlanejadaAfter(
            Long usuarioId, StatusTarefa status, LocalDate data);

    List<Tarefa> findByUsuarioIdAndStatusAndDataPlanejadaBefore(
            Long usuarioId, StatusTarefa status, LocalDate data);

    List<Tarefa> findByUsuarioIdAndStatusAndDataPlanejadaIsNull(Long usuarioId, StatusTarefa status);

    List<Tarefa> findByUsuarioIdAndStatusOrderByConcluidoEmDesc(Long usuarioId, StatusTarefa status);
}
