package br.com.tasky.dominio;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Agenda-modelo reutilizavel (ex.: "Dia util", "Fim de semana"). */
@Entity
@Table(name = "modelo_dia")
@Getter
@Setter
@NoArgsConstructor
public class ModeloDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false)
    private boolean padrao = false;

    @OneToMany(mappedBy = "modeloDia", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC, horaInicio ASC")
    private List<BlocoModelo> blocos = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long versao;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
