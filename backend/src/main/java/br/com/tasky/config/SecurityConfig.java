package br.com.tasky.config;

import br.com.tasky.security.ClienteHeaderFilter;
import br.com.tasky.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthProperties propriedades;
    private final JwtAuthenticationFilter jwtFilter;
    private final ClienteHeaderFilter clienteHeaderFilter;

    public SecurityConfig(AuthProperties propriedades,
                          JwtAuthenticationFilter jwtFilter,
                          ClienteHeaderFilter clienteHeaderFilter) {
        this.propriedades = propriedades;
        this.jwtFilter = jwtFilter;
        this.clienteHeaderFilter = clienteHeaderFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // A API nao usa sessao nem formulario, entao a protecao CSRF
                // padrao do Spring nao se aplica. Os dois endpoints que se
                // autenticam por cookie sao protegidos pelo ClienteHeaderFilter.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                // Sem isto o Spring redirecionaria para uma tela de login que
                // nao existe; uma API deve responder 401.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // O preflight nao carrega credenciais e precisa passar
                        // antes de qualquer requisicao autenticada cross-origin.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())

                .addFilterBefore(clienteHeaderFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        if (propriedades.corsHabilitado()) {
            http.cors(Customizer.withDefaults());
        }

        return http.build();
    }

    /**
     * CORS so existe quando front e API estao em origens diferentes. Na
     * configuracao de origem unica este bean nem e criado, e nao ha
     * cross-origin para autorizar.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(propriedades.corsOrigem()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", ClienteHeaderFilter.HEADER));
        // Necessario para o cookie de refresh atravessar origens.
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", config);
        return fonte;
    }
}
