package br.com.tasky.web;

import br.com.tasky.RelogioAjustavel;
import br.com.tasky.TestcontainersConfiguration;
import br.com.tasky.entity.Usuario;
import br.com.tasky.repository.RefreshTokenRepository;
import br.com.tasky.repository.UsuarioRepository;
import br.com.tasky.security.TokenAcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O fuso que o aparelho manda ao abrir o app.
 *
 * O relogio para perto da meia-noite de Sao Paulo de proposito: e ali que Sao
 * Paulo e Toquio estao em dias diferentes, e o efeito da troca aparece.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, UsuarioControllerTest.RelogioDeTesteConfig.class})
class UsuarioControllerTest {

    /** 25/09/2026, 20:30 em Sao Paulo - ja 26/09, 08:30 em Toquio. */
    private static final Instant AGORA = Instant.parse("2026-09-25T23:30:00Z");

    @TestConfiguration
    static class RelogioDeTesteConfig {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioAjustavel(AGORA);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenAcessoService tokenAcessoService;
    @Autowired private Clock clock;

    private Usuario usuario;
    private String token;

    @BeforeEach
    void preparar() {
        ((RelogioAjustavel) clock).definir(AGORA);

        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();

        var novo = new Usuario();
        novo.setEmail("carlos@tasky.app");
        novo.setSenhaHash("nao-importa");
        novo.setNomeExibicao("Carlos");
        novo.setFusoHorario("America/Sao_Paulo");
        usuario = usuarioRepository.save(novo);
        token = tokenAcessoService.gerar(usuario).valor();
    }

    @Test
    @DisplayName("trocar o fuso muda o dia que o app enxerga")
    void trocaMudaODia() throws Exception {
        buscarDia().andExpect(jsonPath("$.data").value("2026-09-25"));

        definirFuso("Asia/Tokyo").andExpect(status().isNoContent());

        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getFusoHorario())
                .isEqualTo("Asia/Tokyo");
        buscarDia().andExpect(jsonPath("$.data").value("2026-09-26"));
    }

    @Test
    @DisplayName("mandar o mesmo fuso de novo nao e erro")
    void mesmoFusoEIdempotente() throws Exception {
        definirFuso("America/Sao_Paulo").andExpect(status().isNoContent());
        definirFuso("America/Sao_Paulo").andExpect(status().isNoContent());

        buscarDia().andExpect(jsonPath("$.data").value("2026-09-25"));
    }

    @Test
    @DisplayName("fuso desconhecido e recusado, e o que estava gravado fica")
    void fusoInvalido() throws Exception {
        definirFuso("Marte/Olympus")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(
                        org.hamcrest.Matchers.containsString("Marte/Olympus")));

        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getFusoHorario())
                .isEqualTo("America/Sao_Paulo");
    }

    @Test
    @DisplayName("fuso em branco nao passa da validacao")
    void fusoEmBranco() throws Exception {
        definirFuso("  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("fusoHorario"));
    }

    @Test
    @DisplayName("sem sessao, nao da para mexer no fuso de ninguem")
    void exigeSessao() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/eu/fuso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fusoHorario\":\"Asia/Tokyo\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ apoio

    private ResultActions definirFuso(String fuso) throws Exception {
        return mockMvc.perform(put("/api/v1/usuarios/eu/fuso")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fusoHorario\":\"" + fuso + "\"}"));
    }

    private ResultActions buscarDia() throws Exception {
        return mockMvc.perform(get("/api/v1/dia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
