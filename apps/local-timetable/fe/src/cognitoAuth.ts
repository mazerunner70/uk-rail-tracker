const STORAGE_PREFIX = "urt-lt.cognito.";
const KEY_VERIFIER = `${STORAGE_PREFIX}code_verifier`;
const KEY_STATE = `${STORAGE_PREFIX}oauth_state`;
const KEY_TOKENS = `${STORAGE_PREFIX}tokens`;

export type CognitoConfig = {
  domain: string;
  clientId: string;
  redirectUri: string;
  logoutUri: string;
  scopes?: string[];
};

export type CognitoTokens = {
  accessToken: string;
  idToken: string;
  refreshToken?: string;
  expiresAt: number;
};

export type CognitoAuth = {
  isConfigured: true;
  completeRedirectIfPresent: () => Promise<boolean>;
  signIn: () => Promise<void>;
  signOut: () => void;
  isAuthenticated: () => boolean;
  /** ID token — API Gateway Cognito JWT authorizer validates `aud` = app client id. */
  getBearerToken: () => Promise<string | null>;
};

function b64url(bytes: ArrayBuffer | Uint8Array): string {
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = "";
  for (const b of u8) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomBytes(n: number): Uint8Array {
  const out = new Uint8Array(n);
  crypto.getRandomValues(out);
  return out;
}

async function sha256(input: string): Promise<ArrayBuffer> {
  return crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
}

function readTokens(): CognitoTokens | null {
  const raw = sessionStorage.getItem(KEY_TOKENS);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as CognitoTokens;
    if (!parsed.accessToken || !parsed.expiresAt) return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeTokens(tokens: CognitoTokens): void {
  sessionStorage.setItem(KEY_TOKENS, JSON.stringify(tokens));
}

function clearTokens(): void {
  sessionStorage.removeItem(KEY_TOKENS);
  sessionStorage.removeItem(KEY_VERIFIER);
  sessionStorage.removeItem(KEY_STATE);
}

function tokenEndpoint(domain: string): string {
  return `https://${domain}/oauth2/token`;
}

function authorizeUrl(cfg: CognitoConfig, challenge: string, state: string): string {
  const scopes = (cfg.scopes ?? ["openid", "email", "profile"]).join(" ");
  const q = new URLSearchParams({
    client_id: cfg.clientId,
    response_type: "code",
    scope: scopes,
    redirect_uri: cfg.redirectUri,
    code_challenge_method: "S256",
    code_challenge: challenge,
    state,
  });
  return `https://${cfg.domain}/oauth2/authorize?${q.toString()}`;
}

async function exchangeCode(
  cfg: CognitoConfig,
  code: string,
  verifier: string,
): Promise<CognitoTokens> {
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: cfg.clientId,
    code,
    redirect_uri: cfg.redirectUri,
    code_verifier: verifier,
  });
  const res = await fetch(tokenEndpoint(cfg.domain), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) {
    throw new Error(`token exchange failed: ${res.status}`);
  }
  const json = (await res.json()) as {
    access_token: string;
    id_token: string;
    refresh_token?: string;
    expires_in: number;
  };
  return {
    accessToken: json.access_token,
    idToken: json.id_token,
    refreshToken: json.refresh_token,
    expiresAt: Date.now() + Math.max(0, json.expires_in - 60) * 1000,
  };
}

async function refreshAccessToken(
  cfg: CognitoConfig,
  refreshToken: string,
): Promise<CognitoTokens> {
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: cfg.clientId,
    refresh_token: refreshToken,
  });
  const res = await fetch(tokenEndpoint(cfg.domain), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) {
    throw new Error(`refresh failed: ${res.status}`);
  }
  const json = (await res.json()) as {
    access_token: string;
    id_token: string;
    expires_in: number;
  };
  return {
    accessToken: json.access_token,
    idToken: json.id_token,
    refreshToken,
    expiresAt: Date.now() + Math.max(0, json.expires_in - 60) * 1000,
  };
}

export function readCognitoConfigFromDocument(
  doc: Document = document,
): CognitoConfig | null {
  const domain = doc.querySelector('meta[name="cognito-domain"]')?.getAttribute("content")?.trim() ?? "";
  const clientId =
    doc.querySelector('meta[name="cognito-client-id"]')?.getAttribute("content")?.trim() ?? "";
  if (!domain || !clientId) return null;
  const origin = `${window.location.origin}/`;
  const redirectMeta =
    doc.querySelector('meta[name="cognito-redirect-uri"]')?.getAttribute("content")?.trim() ?? "";
  const logoutMeta =
    doc.querySelector('meta[name="cognito-logout-uri"]')?.getAttribute("content")?.trim() ?? "";
  const redirectUri = (redirectMeta || origin).replace(/\/?$/, "/");
  const logoutUri = (logoutMeta || origin).replace(/\/?$/, "/");
  return {
    domain: domain.replace(/^https?:\/\//, "").replace(/\/$/, ""),
    clientId,
    redirectUri,
    logoutUri,
  };
}

export function createCognitoAuth(cfg: CognitoConfig): CognitoAuth {
  return {
    isConfigured: true,

    async completeRedirectIfPresent() {
      const url = new URL(window.location.href);
      const code = url.searchParams.get("code");
      const state = url.searchParams.get("state");
      const err = url.searchParams.get("error");
      if (err) {
        clearTokens();
        throw new Error(`cognito: ${err}`);
      }
      if (!code) return false;
      const expectedState = sessionStorage.getItem(KEY_STATE);
      const verifier = sessionStorage.getItem(KEY_VERIFIER);
      if (!verifier || !expectedState || state !== expectedState) {
        clearTokens();
        throw new Error("cognito: oauth state/verifier mismatch");
      }
      const tokens = await exchangeCode(cfg, code, verifier);
      writeTokens(tokens);
      sessionStorage.removeItem(KEY_VERIFIER);
      sessionStorage.removeItem(KEY_STATE);
      url.searchParams.delete("code");
      url.searchParams.delete("state");
      window.history.replaceState({}, document.title, url.pathname + url.search + url.hash);
      return true;
    },

    async signIn() {
      const verifier = b64url(randomBytes(32));
      const challenge = b64url(await sha256(verifier));
      const state = b64url(randomBytes(16));
      sessionStorage.setItem(KEY_VERIFIER, verifier);
      sessionStorage.setItem(KEY_STATE, state);
      window.location.assign(authorizeUrl(cfg, challenge, state));
    },

    signOut() {
      clearTokens();
      const q = new URLSearchParams({
        client_id: cfg.clientId,
        logout_uri: cfg.logoutUri,
      });
      window.location.assign(`https://${cfg.domain}/logout?${q.toString()}`);
    },

    isAuthenticated() {
      const t = readTokens();
      return Boolean(t?.accessToken);
    },

    async getBearerToken() {
      let t = readTokens();
      if (!t) return null;
      if (Date.now() < t.expiresAt) return t.idToken;
      if (!t.refreshToken) {
        clearTokens();
        return null;
      }
      try {
        t = await refreshAccessToken(cfg, t.refreshToken);
        writeTokens(t);
        return t.idToken;
      } catch {
        clearTokens();
        return null;
      }
    },
  };
}
