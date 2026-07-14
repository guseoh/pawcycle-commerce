export class CsrfRefreshError extends Error {
  constructor() {
    super("보안 정보를 갱신하지 못했습니다.");
    this.name = "CsrfRefreshError";
  }
}

interface CsrfRequestOptions<T> {
  currentToken: string | null;
  acquireToken: () => Promise<string>;
  setToken: (token: string | null) => void;
  request: (token: string) => Promise<T>;
  isCsrfInvalid: (error: unknown) => boolean;
  refreshAfterSuccess?: boolean;
}

async function acquireFreshToken(
  acquireToken: () => Promise<string>,
  setToken: (token: string | null) => void,
): Promise<string> {
  setToken(null);
  try {
    const token = await acquireToken();
    setToken(token);
    return token;
  } catch {
    setToken(null);
    throw new CsrfRefreshError();
  }
}

export async function runCsrfRequest<T>({
  currentToken,
  acquireToken,
  setToken,
  request,
  isCsrfInvalid,
  refreshAfterSuccess = false,
}: CsrfRequestOptions<T>): Promise<T> {
  const token = currentToken ?? (await acquireFreshToken(acquireToken, setToken));

  try {
    const result = await request(token);
    if (refreshAfterSuccess) {
      try {
        await acquireFreshToken(acquireToken, setToken);
      } catch (error) {
        if (!(error instanceof CsrfRefreshError)) throw error;
        setToken(null);
      }
    }
    return result;
  } catch (error) {
    if (isCsrfInvalid(error)) {
      await acquireFreshToken(acquireToken, setToken);
    }
    throw error;
  }
}

export function clearAuthentication(
  setMemberId: (memberId: null) => void,
  setToken: (token: null) => void,
  setAnonymous: () => void,
): void {
  setMemberId(null);
  setToken(null);
  setAnonymous();
}
