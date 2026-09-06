package br.com.tasky.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalTime;

/** Intervalo nomeado dentro de um ModeloDia (ex.: "Foco no trabalho" 09:00-12:00). */
@Entity
@Table(name = "bloco_modelo")
@Getter
@Setter
@NoArgsConstructor
public class BlocoModelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modelo_dia_id", nullable = false)
    private ModeloDia modeloDia;

    @Column(nullable = false, length = 200)
    private String titulo;

    /** O banco exige horaFim > horaInicio: bloco cruzando a meia-noite nao e suportado no MVP. */
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(length = 20)
    private String cor;

    @Column(nullable = false)
    private int ordem = 0;

    /** Minutos de antecedencia do lembrete. Nulo = sem lembrete para este bloco. */
    @Column(name = "minutos_antecedencia_lembrete")
    private Integer minutosAntecedenciaLembrete;

    @Version
    @Column(nullable = false)
    private long versao;
}
