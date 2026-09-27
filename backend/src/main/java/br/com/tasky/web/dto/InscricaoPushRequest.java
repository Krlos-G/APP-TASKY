package br.com.tasky.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O que o navegador entrega ao inscrever o aparelho no servico de push. */
public record InscricaoPushRequest(
        @NotBlank(message = "informe o endpoint") @Size(max = 1000) String endpoint,
        @NotBlank(message = "informe a chave p256dh") @Size(max = 255) String p256dh,
        @NotBlank(message = "informe o segredo auth") @Size(max = 255) String auth,
        @Size(max = 400) String userAgent) {
}
