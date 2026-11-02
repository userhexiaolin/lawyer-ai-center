export function useAccess() {
  return {
    hasAccessByCodes: (codes: string[]) =>
      new URLSearchParams(location.search).get('scenario') !== 'readonly' ||
      codes.some((code) => code.endsWith(':list') || code.endsWith(':query')),
  };
}
