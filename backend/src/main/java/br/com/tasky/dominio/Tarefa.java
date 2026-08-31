package br.com.tasky.dominio;

import br.com.tasky.dominio.enums.Prioridade;
import br.com.tasky.dominio.enums.StatusTarefa;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tarefa")
@Getter
@Setter
@NoArgsConstructor
public class Tarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(length = 5000)
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridade prioridade = Prioridade.MEDIA;

    @Column(name = "minutos_estimados")
    private Integer minutosEstimados;

    /** Prazo real da tarefa. Vencido = badge, mas nao muda o status. */
    @Column(name = "data_limite")
    private LocalDate dataLimite;

    /** Dia em que se pretende fazer. E o que a coloca na tela Hoje. */
    @Column(name = "data_planejada")
    private LocalDate dataPlanejada;

    /** Nulo = tarefa do dia sem horario definido. */
    @Column(name = "hora_planejada")
    private LocalTime horaPlanejada;

    /** Se o bloco for apagado, volta a nulo (ON DELETE SET NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bloco_planejado_id")
    private BlocoModelo blocoPlanejado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusTarefa status = StatusTarefa.A_FAZER;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @Column(name = "hora_lembrete")
    private LocalTime horaLembrete;

    @Version
    @Column(nullable = false)
    private long versao;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public boolean isAtrasada(LocalDate hoje) {
        return status == StatusTarefa.A_FAZER
                && dataPlanejada != null
                && dataPlanejada.isBefore(hoje);
    }
}
