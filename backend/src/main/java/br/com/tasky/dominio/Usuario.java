package br.com.tasky.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(name = "nome_exibicao", nullable = false, length = 100)
    private String nomeExibicao;

    /** Fuso IANA (ex.: America/Sao_Paulo). Define quando o dia vira para este usuario. */
    @Column(name = "fuso_horario", nullable = false, length = 64)
    private String fusoHorario = "America/Sao_Paulo";

    /** Horario do resumo "bom dia". Nulo desliga o resumo. */
    @Column(name = "hora_resumo_diario")
    private LocalTime horaResumoDiario;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public ZoneId zona() {
        return ZoneId.of(fusoHorario);
    }
}
