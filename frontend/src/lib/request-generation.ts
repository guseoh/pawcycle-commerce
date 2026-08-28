export function isLatestRequest(generation: number, currentGeneration: number): boolean {
  return generation === currentGeneration;
}
