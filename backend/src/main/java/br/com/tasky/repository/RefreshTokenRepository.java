package br.com.tasky.repository;

import br.com.tasky.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Busca o token ja trazendo o usuario junto.
     *
     * O JOIN FETCH nao e otimizacao: o usuario e devolvido para fora da
     * transacao da rotacao, e sem ele carregado o acesso posterior estouraria
     * LazyInitializationException.
     */
    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.usuario WHERE t.tokenHash = :hash")
    Optional<RefreshToken> buscarPorHashComUsuario(@Param("hash") String hash);

    /**
     * Revoga o token apenas se ele ainda estiver ativo, devolvendo quantas
     * linhas mudaram.
     *
     * O "apenas se ativo" e o que torna a rotacao segura sob concorrencia: se
     * duas requisicoes apresentarem o mesmo token ao mesmo tempo, so uma
     * consegue revogar. A outra recebe zero linhas e sabe que perdeu a corrida,
     * em vez de as duas seguirem adiante achando que ganharam.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revogadoEm = :agora
             WHERE t.id = :id
               AND t.revogadoEm IS NULL
            """)
    int revogarSeAtivo(@Param("id") Long id, @Param("agora") Instant agora);

    /**
     * A familia ainda tem algum token utilizavel?
     *
     * E o que separa uma corrida entre abas de uma sessao encerrada. Numa
     * corrida, o token sucessor existe e esta ativo. Depois de um logout ou de
     * uma revogacao por replay, a familia inteira esta revogada - e conceder
     * graca ali desfaria justamente a revogacao.
     */
    @Query("""
            SELECT COUNT(t) > 0
              FROM RefreshToken t
             WHERE t.familia = :familia
               AND t.revogadoEm IS NULL
               AND t.expiraEm > :agora
            """)
    boolean existeAtivoNaFamilia(@Param("familia") UUID familia, @Param("agora") Instant agora);

    /** Revoga a familia inteira. Usado no logout e ao detectar replay. */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revogadoEm = :agora
             WHERE t.familia = :familia
               AND t.revogadoEm IS NULL
            """)
    int revogarFamilia(@Param("familia") UUID familia, @Param("agora") Instant agora);

    /** Usado para encerrar todas as sessoes do usuario de uma vez. */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revogadoEm = :agora
             WHERE t.usuario.id = :usuarioId
               AND t.revogadoEm IS NULL
            """)
    int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    /** Limpeza periodica: tokens vencidos ha tempo nao servem nem para auditoria. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiraEm < :limite")
    int apagarExpiradosAntesDe(@Param("limite") Instant limite);
}
