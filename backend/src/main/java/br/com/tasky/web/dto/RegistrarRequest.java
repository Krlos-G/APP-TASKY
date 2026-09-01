package br.com.tasky.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarRequest(

        @NotBlank(message = "informe o e-mail")
        @Email(message = "e-mail invalido")
        @Size(max = 255)
        String email,

        // Tamanho minimo generoso em vez de regras de composicao: comprimento
        // contribui mais para a forca da senha do que exigir simbolos.
        @NotBlank(message = "informe a senha")
        @Size(min = 12, max = 100, message = "a senha precisa ter ao menos 12 caracteres")
        String senha,

        @NotBlank(message = "informe o nome")
        @Size(max = 100)
        String nomeExibicao,

        @Size(max = 64)
        String fusoHorario,

        @NotBlank(message = "informe o codigo de convite")
        String codigoConvite) {
}
