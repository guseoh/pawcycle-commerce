type Loaders<T> = { [K in keyof T]: () => Promise<T[K]> };

/** One mounted/member lifetime; cancellation and auth expiry invalidate every pending response. */
export function runSummaryRequests<T>(
  loaders: Loaders<T>,
  keys: (keyof T)[],
  handlers: {
    value: <K extends keyof T>(key: K, value: T[K]) => void;
    error: (key: keyof T) => void;
    expired: () => void;
    settled: () => void;
    isAuthRequired: (reason: unknown) => boolean;
  },
) {
  let active = true;
  const done = Promise.all(keys.map(async key => {
    try {
      const value = await loaders[key]();
      if (active) handlers.value(key, value);
    } catch (reason) {
      if (!active) return;
      if (handlers.isAuthRequired(reason)) {
        active = false;
        handlers.expired();
      } else handlers.error(key);
    }
  })).then(() => { if (active) handlers.settled(); });
  return { done, cancel: () => { active = false; } };
}
