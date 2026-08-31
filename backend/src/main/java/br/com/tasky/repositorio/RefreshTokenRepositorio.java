package br.com.tasky.repositorio;

import br.com.tasky.dominio.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepositorio extends JpaRepository<RefreshToken, Long> {
}
