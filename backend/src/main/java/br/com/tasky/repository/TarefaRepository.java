package br.com.tasky.repository;

import br.com.tasky.entity.Tarefa;
import br.com.tasky.entity.enums.StatusTarefa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** Fim exclusivo: uma conclusao na virada nao pode contar nas duas semanas. */
    @Query("""
            SELECT count(t) FROM Tarefa t
             WHERE t.usuario.id = :usuarioId
               AND t.status = br.com.tasky.entity.enums.StatusTarefa.FEITA
               AND t.concluidoEm >= :inicio
               AND t.concluidoEm < :fim
            """)
    long contarConcluidasEntre(@Param("usuarioId") Long usuarioId,
                               @Param("inicio") Instant inicio,
                               @Param("fim") Instant fim);
}
