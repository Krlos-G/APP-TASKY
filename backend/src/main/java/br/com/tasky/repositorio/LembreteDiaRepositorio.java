package br.com.tasky.repositorio;

import br.com.tasky.dominio.LembreteDia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LembreteDiaRepositorio extends JpaRepository<LembreteDia, Long> {
}
