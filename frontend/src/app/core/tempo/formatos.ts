/** "2026-09-16" → "16/09", sem passar por Date: o parse em UTC voltaria um dia. */
export function formatarDataCurta(iso: string): string {
  const [, mes, dia] = iso.split('-');
  return `${dia}/${mes}`;
}

/** "1h12" lê melhor que "72 minutos". */
export function formatarDuracao(minutos: number): string {
  if (minutos < 60) {
    return `${minutos} min`;
  }
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  return resto === 0 ? `${horas}h` : `${horas}h${String(resto).padStart(2, '0')}`;
}

/** "2026-09-24" → Date local. Nunca `new Date(iso)`: ele parseia em UTC. */
export function dataDeIso(iso: string): Date {
  const [ano, mes, dia] = iso.split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

/** "quinta-feira, 24 de setembro" */
export function formatarDataPorExtenso(iso: string): string {
  return dataDeIso(iso).toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
}

export function formatarStreak(streak: number): string {
  if (streak === 0) {
    return 'sem sequência';
  }
  return streak === 1 ? '1 dia seguido' : `${streak} dias seguidos`;
}
