package br.com.tasky.repository;

import br.com.tasky.entity.AtribuicaoDia;
import br.com.tasky.entity.enums.DiaSemana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AtribuicaoDiaRepository extends JpaRepository<AtribuicaoDia, Long> {

    /**
     * Traz o modelo junto: a resposta expoe o nome dele, e com open-in-view
     * desligado o acesso preguicoso fora da transacao estoura.
     */
    @Query("""
            SELECT a FROM AtribuicaoDia a
              JOIN FETCH a.modeloDia
             WHERE a.usuario.id = :usuarioId
            """)
    List<AtribuicaoDia> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    /** Usada pela montagem do dia: qual modelo vale nesta data. */
    @Query("""
            SELECT a FROM AtribuicaoDia a
              JOIN FETCH a.modeloDia m
              LEFT JOIN FETCH m.blocos
             WHERE a.usuario.id = :usuarioId
               AND a.diaSemana = :diaSemana
            """)
    Optional<AtribuicaoDia> findByUsuarioIdAndDiaSemana(@Param("usuarioId") Long usuarioId,
                                                        @Param("diaSemana") DiaSemana diaSemana);

    /** Em quais dias este modelo esta em uso - impede apagar sem avisar. */
    List<AtribuicaoDia> findByUsuarioIdAndModeloDiaId(Long usuarioId, Long modeloDiaId);

    void deleteByUsuarioId(Long usuarioId);
}
