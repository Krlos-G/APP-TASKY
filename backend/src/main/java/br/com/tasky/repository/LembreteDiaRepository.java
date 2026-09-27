package br.com.tasky.repository;

import br.com.tasky.entity.LembreteDia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LembreteDiaRepository extends JpaRepository<LembreteDia, Long> {

    List<LembreteDia> findByUsuarioIdAndDataRefBetween(
            Long usuarioId, LocalDate inicio, LocalDate fim);
}
