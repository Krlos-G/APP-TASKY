package br.com.tasky.repository;

import br.com.tasky.entity.InscricaoPush;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InscricaoPushRepository extends JpaRepository<InscricaoPush, Long> {

    /**
     * Sem filtro por dono de proposito: o endpoint e unico no banco, e o mesmo
     * aparelho pode ter trocado de conta. Quem chama decide o que fazer.
     */
    Optional<InscricaoPush> findByEndpoint(String endpoint);

    Optional<InscricaoPush> findByEndpointAndUsuarioId(String endpoint, Long usuarioId);

    List<InscricaoPush> findByUsuarioId(Long usuarioId);
}
