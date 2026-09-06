package br.com.tasky.repository;

import br.com.tasky.entity.ModeloDia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModeloDiaRepository extends JpaRepository<ModeloDia, Long> {

    /**
     * Toda leitura filtra pelo dono.
     *
     * Nao existe busca por id sozinha de proposito: e o tipo de metodo que
     * alguem usaria sem lembrar de conferir a propriedade depois.
     */
    @EntityGraph(attributePaths = "blocos")
    List<ModeloDia> findByUsuarioIdOrderByNomeAsc(Long usuarioId);

    @EntityGraph(attributePaths = "blocos")
    Optional<ModeloDia> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByIdAndUsuarioId(Long id, Long usuarioId);
}
