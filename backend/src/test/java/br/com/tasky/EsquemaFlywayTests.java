package br.com.tasky;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de fundacao da Fatia 1.
 *
 * Sobe um PostgreSQL real, deixa o Flyway aplicar a V1 do zero e confere que o
 * esquema esperado existe. Como o application.yml usa ddl-auto: validate, o
 * simples fato do contexto Spring carregar ja prova que todas as entidades JPA
 * batem com as tabelas criadas pela migracao - as duas coisas que mais quebram
 * silenciosamente numa fundacao.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EsquemaFlywayTests {

    private static final List<String> TABELAS_ESPERADAS = List.of(
            "atribuicao_dia",
            "bloco_modelo",
            "habito",
            "inscricao_push",
            "lembrete_dia",
            "modelo_dia",
            "refresh_token",
            "registro_habito",
            "tarefa",
            "usuario");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a migracao V1 aplica limpo num banco zerado")
    void migracaoAplicaLimpo() {
        List<String> aplicadas = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);

        assertThat(aplicadas).contains("1");
    }

    @Test
    @DisplayName("todas as tabelas do esquema inicial existem")
    void todasAsTabelasExistem() {
        List<String> tabelas = jdbcTemplate.queryForList(
                """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_type = 'BASE TABLE'
                   AND table_name <> 'flyway_schema_history'
                 ORDER BY table_name
                """,
                String.class);

        assertThat(tabelas).containsExactlyElementsOf(TABELAS_ESPERADAS);
    }

    @Test
    @DisplayName("as constraints que sustentam regras de negocio estao no banco")
    void constraintsCriticasExistem() {
        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE connamespace = 'public'::regnamespace",
                String.class);

        assertThat(constraints).contains(
                // um habito so pode ter um registro por dia
                "uk_registro_habito",
                // um modelo por dia da semana, por usuario
                "uk_atribuicao_dia",
                // bloco nao pode cruzar a meia-noite no MVP
                "ck_bloco_modelo_horario",
                // impede lembrete duplicado, inclusive o de RESUMO (origem_id nulo)
                "uk_lembrete_dia_origem");
    }

    @Test
    @DisplayName("o indice usado pelo despachante de lembretes existe")
    void indiceDoDespachanteExiste() {
        List<String> indices = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                String.class);

        assertThat(indices).contains("ix_lembrete_dia_despacho");
    }
}
