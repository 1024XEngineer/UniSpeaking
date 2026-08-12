export function compactPageNumbers(currentPage: number, totalPages: number) {
  const safeTotal = Math.max(1, totalPages);
  const safeCurrent = Math.min(safeTotal, Math.max(1, currentPage));
  if (safeTotal <= 3) {
    return Array.from({ length: safeTotal }, (_, index) => index + 1);
  }
  if (safeCurrent <= 2) return [1, 2, 3];
  if (safeCurrent >= safeTotal - 1) {
    return [safeTotal - 2, safeTotal - 1, safeTotal];
  }
  return [safeCurrent - 1, safeCurrent, safeCurrent + 1];
}
