export interface CorDisponivel {
  nome: string;
  valor: string;
}

/**
 * Paleta fixa de habitos e blocos, tirada das cores de sistema do iOS.
 *
 * Escolher cor aqui e personalizacao, nao configuracao: abrir o seletor do
 * sistema inteiro pede mais decisao do que a coisa merece, e ainda deixa
 * entrar tom que briga com o resto do app.
 */
export const CORES: CorDisponivel[] = [
  { nome: 'Vermelho', valor: '#FF3B30' },
  { nome: 'Laranja', valor: '#FF9500' },
  { nome: 'Amarelo', valor: '#FFCC00' },
  { nome: 'Verde', valor: '#34C759' },
  { nome: 'Turquesa', valor: '#00C7BE' },
  { nome: 'Azul', valor: '#007AFF' },
  { nome: 'Índigo', valor: '#5856D6' },
  { nome: 'Roxo', valor: '#AF52DE' },
  { nome: 'Rosa', valor: '#FF2D55' },
  { nome: 'Marrom', valor: '#A2845E' },
];

export const COR_PADRAO = '#007AFF';

/**
 * A paleta, mais a cor que o item ja tem quando ela veio do seletor antigo.
 *
 * Sem isto, abrir a edicao de um habito criado antes mostraria a lista com
 * nada selecionado - como se a cor dele tivesse sumido.
 */
export function coresCom(atual: string | null | undefined): CorDisponivel[] {
  if (!atual || CORES.some((cor) => cor.valor.toLowerCase() === atual.toLowerCase())) {
    return CORES;
  }
  return [{ nome: 'Cor atual', valor: atual }, ...CORES];
}
