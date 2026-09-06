/** Resposta de /login e /refresh. O refresh token nao vem aqui: ele so existe no cookie. */
export interface RespostaToken {
  accessToken: string;
  expiraEm: string;
}

/** Usuario autenticado, como devolvido por /auth/eu. */
export interface UsuarioAutenticado {
  id: number;
  email: string;
  nome: string;
}

export interface CredenciaisLogin {
  email: string;
  senha: string;
}

export interface DadosRegistro {
  email: string;
  senha: string;
  nomeExibicao: string;
  fusoHorario?: string;
  codigoConvite: string;
}

/** Erro de validacao por campo, no formato devolvido pelo backend. */
export interface ErroCampo {
  campo: string;
  mensagem: string;
}

export interface RespostaErro {
  erro: string;
  campos?: ErroCampo[];
}
