/** Null means cancel. Acquire the lock synchronously, before any API work. */
export function createOperationExecutor() {
  let busy = false;
  return async (operation: (() => Promise<void>) | null): Promise<boolean> => {
    if (!operation || busy) return false;
    busy = true;
    try {
      await operation();
      return true;
    } finally {
      busy = false;
    }
  };
}
