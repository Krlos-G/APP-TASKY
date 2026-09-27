package br.com.tasky.repository;

import br.com.tasky.entity.LembreteDia;
import br.com.tasky.entity.enums.StatusLembrete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface LembreteDiaRepository extends JpaRepository<LembreteDia, Long> {

    List<LembreteDia> findByUsuarioIdAndDataRefBetween(
            Long usuarioId, LocalDate inicio, LocalDate fim);

    /** A consulta do despachante, a cada 60s. O indice da V1 cobre exatamente esta. */
    List<LembreteDia> findByStatusAndDispararEmLessThanEqualOrderByDispararEm(
            StatusLembrete status, Instant limite);
}
