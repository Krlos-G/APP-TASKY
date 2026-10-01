package br.com.tasky.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serve o build do Angular no mesmo endereco da API.
 *
 * Mesma origem de proposito: o Safari bloqueia cookie de terceiro, e o cookie
 * de renovacao da sessao viraria um se o app e a API morassem em dominios
 * diferentes - o iPhone derrubaria o login.
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                // Sem data de modificacao: dentro do jar ela pode repetir entre
                // builds, e o navegador receberia "nao mudou" para um app que mudou.
                .setUseLastModified(false)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new RotasDoApp());
    }

    /**
     * Um caminho como /tarefas/15 nao e arquivo: e uma rota que o roteador do
     * Angular resolve no navegador. Quem abre esse endereco direto recebe o app.
     */
    private static final class RotasDoApp extends PathResourceResolver {

        @Override
        protected Resource getResource(String caminho, Resource local) throws IOException {
            Resource arquivo = super.getResource(caminho, local);
            if (arquivo != null || caminho.startsWith("api/") || caminho.contains(".")) {
                return arquivo;
            }
            return super.getResource("index.html", local);
        }
    }
}
