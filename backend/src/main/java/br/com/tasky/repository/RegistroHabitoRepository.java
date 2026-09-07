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

    /**
     * O lado de leitura do upsert: ja existe linha para este dia?
     *
     * O unique (habito_id, data) garante que a resposta e no maximo uma.
     */
    Optional<RegistroHabito> findByHabitoIdAndData(Long habitoId, LocalDate data);

    /**
     * As marcacoes de todos os habitos do usuario num periodo, em uma consulta.
     *
     * O streak de N habitos precisa do historico de N habitos; buscar um a um
     * seria N+1 numa tela que abre o tempo todo.
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

    /**
     * A mesma projecao para um habito so: historico da tela e streak individual.
     *
     * O filtro por usuario e redundante quando o servico ja carregou o habito
     * pelo dono, mas custa um join e fecha a porta para o dia em que alguem
     * chamar isto sem ter feito essa checagem antes.
     */
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

    /**
     * Desmarcar e idempotente: apagar um dia sem marcacao nao e erro.
     *
     * O delete derivado do Spring Data leria a linha antes de apagar; aqui vai
     * um comando so, e o retorno ja diz se havia algo.
     */
    @Modifying
    @Query("DELETE FROM RegistroHabito r WHERE r.habito.id = :habitoId AND r.data = :data")
    int apagar(@Param("habitoId") Long habitoId, @Param("data") LocalDate data);
}
