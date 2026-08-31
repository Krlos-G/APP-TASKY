package br.com.tasky.dominio;

import br.com.tasky.dominio.conversor.ConversorDiasSemana;
import br.com.tasky.dominio.enums.DiaSemana;
import br.com.tasky.dominio.enums.TipoAgenda;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "habito")
@Getter
@Setter
@NoArgsConstructor
public class Habito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 40)
    private String icone;

    @Column(length = 20)
    private String cor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_agenda", nullable = false, length = 20)
    private TipoAgenda tipoAgenda = TipoAgenda.DIARIO;

    /** Preenchido apenas quando tipoAgenda = DIAS_SEMANA. */
    @Convert(converter = ConversorDiasSemana.class)
    @Column(name = "dias_semana", length = 40)
    private Set<DiaSemana> diasSemana = EnumSet.noneOf(DiaSemana.class);

    /** Preenchido apenas quando tipoAgenda = VEZES_POR_SEMANA (fora do MVP). */
    @Column(name = "meta_semanal")
    private Integer metaSemanal;

    @Column(name = "hora_preferida")
    private LocalTime horaPreferida;

    /** Nulo = sem lembrete para este habito. */
    @Column(name = "hora_lembrete")
    private LocalTime horaLembrete;

    /** Nao nulo = arquivado: some dos ativos, historico preservado. */
    @Column(name = "arquivado_em")
    private Instant arquivadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    /** O habito e "devido" nesta data, segundo a agenda vigente? */
    public boolean devidoEm(LocalDate data) {
        return switch (tipoAgenda) {
            case DIARIO -> true;
            case DIAS_SEMANA -> diasSemana.contains(DiaSemana.de(data.getDayOfWeek()));
            // Sem dia fixo: a cobranca e semanal, nao diaria.
            case VEZES_POR_SEMANA -> false;
        };
    }

    public boolean isArquivado() {
        return arquivadoEm != null;
    }
}
