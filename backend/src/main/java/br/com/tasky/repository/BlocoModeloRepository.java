package br.com.tasky.repository;

import br.com.tasky.entity.BlocoModelo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlocoModeloRepository extends JpaRepository<BlocoModelo, Long> {

    /** O bloco pertence ao usuario atraves do modelo que o contem. */
    Optional<BlocoModelo> findByIdAndModeloDiaUsuarioId(Long id, Long usuarioId);
}
