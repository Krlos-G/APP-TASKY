package br.com.tasky.web.dto;

/**
 * @param habilitado false quando o servidor nao tem chaves VAPID - a tela
 *                   esconde a opcao em vez de oferecer algo que nao funciona
 */
public record ChavePushResponse(boolean habilitado, String chavePublica) {
}
