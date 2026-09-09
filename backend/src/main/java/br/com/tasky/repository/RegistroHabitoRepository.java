package br.com.tasky.repository;

import br.com.tasky.entity.RegistroHabito;
import br.com.tasky.repository.projection.Marcacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RegistroHabitoRepository extends JpaRepository<RegistroHabito, Long> {

    Optional<RegistroHabito> findByHabitoIdAndData(Long habitoId, LocalDate data);

    /**
     * O historico de todos os habitos do usuario em uma consulta: buscar um a um
     * seria N+1 no calculo do streak da listagem.
     */
    @Query("""
            SELECT new br.com.tasky.repository.projection.Marcacao(r.habito.id, r.data, r.status)
              FROM RegistroHabito r
             WHERE r.habito.usuario.id = :usuarioId
               AND r.data BETWEEN :desde AND :ate
            """)
    List<Marcacao> marcacoesDoUsuario(@Param("usuarioId") Long usuarioId,
                                      @Param("desde") LocalDate desde,
                                      @Param("ate") LocalDate ate);

    @Query("""
            SELECT new br.com.tasky.repository.projection.Marcacao(r.habito.id, r.data, r.status)
              FROM RegistroHabito r
             WHERE r.habito.id = :habitoId
               AND r.habito.usuario.id = :usuarioId
               AND r.data BETWEEN :desde AND :ate
             ORDER BY r.data DESC
            """)
    List<Marcacao> marcacoesDoHabito(@Param("habitoId") Long habitoId,
                                     @Param("usuarioId") Long usuarioId,
                                     @Param("desde") LocalDate desde,
                                     @Param("ate") LocalDate ate);

    /** Um comando so, e o retorno ja diz se havia marcacao para apagar. */
    @Modifying
    @Query("DELETE FROM RegistroHabito r WHERE r.habito.id = :habitoId AND r.data = :data")
    int apagar(@Param("habitoId") Long habitoId, @Param("data") LocalDate data);
}
