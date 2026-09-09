package br.com.tasky.repository;

import br.com.tasky.entity.Habito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HabitoRepository extends JpaRepository<Habito, Long> {

    /**
     * Nao existe busca por id sozinha de proposito: e o tipo de metodo que
     * alguem usaria sem lembrar de conferir a propriedade depois.
     */
    Optional<Habito> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Habito> findByUsuarioIdAndArquivadoEmIsNullOrderByNomeAsc(Long usuarioId);

    List<Habito> findByUsuarioIdOrderByNomeAsc(Long usuarioId);
}
