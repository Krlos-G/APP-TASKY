package br.com.tasky.config;

import br.com.tasky.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O app e a API no mesmo endereco. O index.html usado aqui e o de
 * src/test/resources/static; em producao, o build do Angular entra no lugar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FrontendConfigTest {

    private static final String MARCA_DO_APP = "app-de-teste";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("rota do Angular abre o app, sem token")
    void rotaDoAngularAbreOApp() throws Exception {
        mockMvc.perform(get("/hoje"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(MARCA_DO_APP)));
    }

    @Test
    @DisplayName("rota aninhada, aberta direto pelo endereco, tambem abre o app")
    void rotaAninhadaAbreOApp() throws Exception {
        mockMvc.perform(get("/tarefas/15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(MARCA_DO_APP)));
    }

    @Test
    @DisplayName("a raiz encaminha para o app")
    void raizEncaminhaParaOApp() throws Exception {
        // Quem atende a raiz e a pagina de boas-vindas do Spring Boot, por
        // encaminhamento - que o MockMvc registra, mas nao executa.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    @DisplayName("o app nunca fica em cache sem revalidar: deploy novo precisa ser percebido")
    void appNaoFicaEmCache() throws Exception {
        mockMvc.perform(get("/hoje"))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    @DisplayName("rota de API inexistente responde erro em JSON, e nao o app")
    void apiInexistenteNaoViraApp() throws Exception {
        mockMvc.perform(get("/api/v1/nao-existe").with(user("carlos")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").exists())
                .andExpect(content().string(not(containsString(MARCA_DO_APP))));
    }

    @Test
    @DisplayName("a API continua exigindo token")
    void apiContinuaProtegida() throws Exception {
        mockMvc.perform(get("/api/v1/nao-existe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("arquivo que nao existe e 404, e nao o app")
    void arquivoInexistenteE404() throws Exception {
        mockMvc.perform(get("/main-que-nao-existe.js"))
                .andExpect(status().isNotFound());
    }
}
