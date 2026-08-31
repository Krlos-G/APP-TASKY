package br.com.tasky.dominio;

import br.com.tasky.dominio.enums.StatusLembrete;
import br.com.tasky.dominio.enums.TipoOrigemLembrete;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Lembrete ja materializado com horario absoluto.
 *
 * As regras (bloco/habito/tarefa + fuso do usuario) sao convertidas em linhas
 * concretas com disparar_em em Instant, para que o despachante seja uma query
 * simples e idempotente.
 */
@Entity
@Table(name = "lembrete_dia")
@Getter
@Setter
@NoArgsConstructor
public class LembreteDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "disparar_em", nullable = false)
    private Instant dispararEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_origem", nullable = false, length = 10)
    private TipoOrigemLembrete tipoOrigem;

    /** Id do bloco/habito/tarefa. Nulo quando tipoOrigem = RESUMO. */
    @Column(name = "origem_id")
    private Long origemId;

    /** Dia a que o lembrete se refere, no fuso do usuario. Garante idempotencia. */
    @Column(name = "data_ref", nullable = false)
    private LocalDate dataRef;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(length = 500)
    private String corpo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusLembrete status = StatusLembrete.PENDENTE;

    @Column(nullable = false)
    private int tentativas = 0;

    @Column(name = "enviado_em")
    private Instant enviadoEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
