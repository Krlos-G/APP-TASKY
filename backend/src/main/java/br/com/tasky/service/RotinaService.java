package br.com.tasky.service;

import br.com.tasky.entity.AtribuicaoDia;
import br.com.tasky.entity.BlocoModelo;
import br.com.tasky.entity.ModeloDia;
import br.com.tasky.entity.Usuario;
import br.com.tasky.entity.enums.DiaSemana;
import br.com.tasky.repository.AtribuicaoDiaRepository;
import br.com.tasky.repository.BlocoModeloRepository;
import br.com.tasky.repository.ModeloDiaRepository;
import br.com.tasky.security.UsuarioAtual;
import br.com.tasky.web.ApiException;
import br.com.tasky.web.dto.BlocoRequest;
import br.com.tasky.web.dto.ModeloDiaRequest;
import br.com.tasky.web.dto.SemanaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Regras da agenda-modelo: modelos de dia, seus blocos e a semana.
 *
 * Toda operacao parte do usuario autenticado e consulta filtrando por ele. Um
 * recurso de outro dono responde como inexistente - dizer "existe, mas nao e
 * seu" ja entrega informacao a quem sonda.
 */
@Service
public class RotinaService {

    private final ModeloDiaRepository modeloRepository;
    private final BlocoModeloRepository blocoRepository;
    private final AtribuicaoDiaRepository atribuicaoRepository;
    private final UsuarioAtual usuarioAtual;

    public RotinaService(ModeloDiaRepository modeloRepository,
                         BlocoModeloRepository blocoRepository,
                         AtribuicaoDiaRepository atribuicaoRepository,
                         UsuarioAtual usuarioAtual) {
        this.modeloRepository = modeloRepository;
        this.blocoRepository = blocoRepository;
        this.atribuicaoRepository = atribuicaoRepository;
        this.usuarioAtual = usuarioAtual;
    }

    // ---------------------------------------------------------------- modelos

    @Transactional(readOnly = true)
    public List<ModeloDia> listarModelos() {
        return modeloRepository.findByUsuarioIdOrderByNomeAsc(usuarioAtual.idObrigatorio());
    }

    @Transactional(readOnly = true)
    public ModeloDia buscarModelo(Long id) {
        return modeloRepository.findByIdAndUsuarioId(id, usuarioAtual.idObrigatorio())
                .orElseThrow(() -> ApiException.naoEncontrado("Modelo de dia"));
    }

    @Transactional
    public ModeloDia criarModelo(ModeloDiaRequest pedido) {
        Usuario usuario = usuarioAtual.obrigatorio();

        var modelo = new ModeloDia();
        modelo.setUsuario(usuario);
        modelo.setNome(pedido.nome().trim());
        modelo.setPadrao(pedido.padrao());

        if (pedido.padrao()) {
            desmarcarPadraoAtual(usuario.getId());
        }
        return modeloRepository.save(modelo);
    }

    @Transactional
    public ModeloDia atualizarModelo(Long id, ModeloDiaRequest pedido) {
        ModeloDia modelo = buscarModelo(id);
        modelo.setNome(pedido.nome().trim());

        if (pedido.padrao() && !modelo.isPadrao()) {
            desmarcarPadraoAtual(modelo.getUsuario().getId());
        }
        modelo.setPadrao(pedido.padrao());
        return modelo;
    }

    @Transactional
    public void apagarModelo(Long id) {
        ModeloDia modelo = buscarModelo(id);

        // O banco tem ON DELETE RESTRICT nesta FK; conferir antes permite
        // devolver uma mensagem util em vez de um erro de integridade cru.
        List<AtribuicaoDia> emUso = atribuicaoRepository
                .findByUsuarioIdAndModeloDiaId(modelo.getUsuario().getId(), id);

        if (!emUso.isEmpty()) {
            String dias = emUso.stream()
                    .map(a -> a.getDiaSemana().name())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw ApiException.modeloEmUso(dias);
        }

        modeloRepository.delete(modelo);
    }

    // ----------------------------------------------------------------- blocos

    @Transactional
    public ModeloDia adicionarBloco(Long modeloId, BlocoRequest pedido) {
        ModeloDia modelo = buscarModelo(modeloId);
        validarHorario(pedido);

        var bloco = new BlocoModelo();
        bloco.setModeloDia(modelo);
        aplicar(pedido, bloco);
        bloco.setOrdem(modelo.getBlocos().size());

        modelo.getBlocos().add(bloco);
        return modelo;
    }

    /**
     * Devolve o modelo, nao o bloco.
     *
     * O controller precisa do modelo inteiro para recalcular as sobreposicoes,
     * e navegar bloco.getModeloDia() fora da transacao estouraria - a relacao e
     * preguicosa e o open-in-view esta desligado. Resolver aqui dentro mantem o
     * controller sem saber nada de sessao do Hibernate.
     */
    @Transactional
    public ModeloDia atualizarBloco(Long blocoId, BlocoRequest pedido) {
        validarHorario(pedido);
        Long usuarioId = usuarioAtual.idObrigatorio();

        BlocoModelo bloco = blocoRepository
                .findByIdAndModeloDiaUsuarioId(blocoId, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Bloco"));

        aplicar(pedido, bloco);

        return modeloRepository
                .findByIdAndUsuarioId(bloco.getModeloDia().getId(), usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Modelo de dia"));
    }

    @Transactional
    public void apagarBloco(Long blocoId) {
        BlocoModelo bloco = blocoRepository
                .findByIdAndModeloDiaUsuarioId(blocoId, usuarioAtual.idObrigatorio())
                .orElseThrow(() -> ApiException.naoEncontrado("Bloco"));

        // Remover pela colecao do modelo, para o orphanRemoval agir.
        bloco.getModeloDia().getBlocos().remove(bloco);
    }

    // ----------------------------------------------------------------- semana

    @Transactional(readOnly = true)
    public List<AtribuicaoDia> listarSemana() {
        return atribuicaoRepository.findByUsuarioId(usuarioAtual.idObrigatorio());
    }

    /**
     * Substitui a semana inteira de uma vez.
     *
     * Apaga tudo e regrava, em vez de comparar dia a dia: sao no maximo sete
     * linhas, e a versao simples elimina a chance de sobrar atribuicao antiga.
     */
    @Transactional
    public List<AtribuicaoDia> definirSemana(SemanaRequest pedido) {
        Usuario usuario = usuarioAtual.obrigatorio();

        Map<Long, ModeloDia> modelos = modeloRepository
                .findByUsuarioIdOrderByNomeAsc(usuario.getId()).stream()
                .collect(Collectors.toMap(ModeloDia::getId, m -> m));

        var novas = new ArrayList<AtribuicaoDia>();
        pedido.modeloPorDia().forEach((dia, modeloId) -> {
            if (modeloId == null) {
                return; // dia sem rotina
            }
            ModeloDia modelo = modelos.get(modeloId);
            if (modelo == null) {
                throw ApiException.naoEncontrado("Modelo de dia " + modeloId);
            }

            var atribuicao = new AtribuicaoDia();
            atribuicao.setUsuario(usuario);
            atribuicao.setDiaSemana(dia);
            atribuicao.setModeloDia(modelo);
            novas.add(atribuicao);
        });

        atribuicaoRepository.deleteByUsuarioId(usuario.getId());
        // Sem o flush, o INSERT poderia chegar antes do DELETE e esbarrar no
        // unique de (usuario_id, dia_semana).
        atribuicaoRepository.flush();

        return atribuicaoRepository.saveAll(novas);
    }

    /** Qual modelo vale neste dia da semana, se houver. */
    @Transactional(readOnly = true)
    public java.util.Optional<ModeloDia> modeloDoDia(Long usuarioId, DiaSemana dia) {
        return atribuicaoRepository.findByUsuarioIdAndDiaSemana(usuarioId, dia)
                .map(AtribuicaoDia::getModeloDia);
    }

    // ------------------------------------------------------------------ apoio

    private void aplicar(BlocoRequest pedido, BlocoModelo bloco) {
        bloco.setTitulo(pedido.titulo().trim());
        bloco.setHoraInicio(pedido.horaInicio());
        bloco.setHoraFim(pedido.horaFim());
        bloco.setCor(pedido.cor());
        bloco.setMinutosAntecedenciaLembrete(pedido.minutosAntecedenciaLembrete());
    }

    private void validarHorario(BlocoRequest pedido) {
        if (!pedido.horaFim().isAfter(pedido.horaInicio())) {
            throw ApiException.horarioInvalido();
        }
    }

    private void desmarcarPadraoAtual(Long usuarioId) {
        modeloRepository.findByUsuarioIdOrderByNomeAsc(usuarioId).stream()
                .filter(ModeloDia::isPadrao)
                .forEach(m -> m.setPadrao(false));
    }
}
