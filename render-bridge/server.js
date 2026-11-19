const http = require("http");
const fs = require("fs");
const path = require("path");
const { URL } = require("url");

const PORT = Number(process.env.PORT || 3000);
const GOOGLE_CLIENT_ID = (process.env.GOOGLE_CLIENT_ID || "").trim();
const GOOGLE_CLIENT_SECRET = (process.env.GOOGLE_CLIENT_SECRET || "").trim();
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || "").trim().replace(/\/+$/, "");
const APK_DOWNLOAD_URL = (
  process.env.APK_DOWNLOAD_URL ||
  "https://github.com/diegocataldoarellano-hub/catedra-android/releases/latest/download/Catedra-v1.0.0.apk"
).trim();

const PORTAL_HTML_PATH = path.resolve(__dirname, "..", "docs", "index.html");

function getRedirectUri(req) {
  if (PUBLIC_BASE_URL) return `${PUBLIC_BASE_URL}/auth/google/callback`;
  const proto = req.headers["x-forwarded-proto"] || "https";
  const host = req.headers["x-forwarded-host"] || req.headers.host || `localhost:${PORT}`;
  return `${proto}://${host}/auth/google/callback`;
}

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  });
  res.end(body);
}

function sendHtml(res, status, html) {
  res.writeHead(status, {
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store",
  });
  res.end(html);
}

function escapeHtml(str) {
  return String(str || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function readRequestBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (chunk) => {
      data += chunk;
      if (data.length > 64 * 1024) {
        req.destroy();
        reject(new Error("Payload too large"));
      }
    });
    req.on("end", () => resolve(data));
    req.on("error", reject);
  });
}

function renderReturnToAppHtml(deepLink, email, errorText) {
  const safeDeepLink = escapeHtml(deepLink);
  const title = errorText ? "No se pudo conectar Google Drive" : "Google Drive conectado";
  const subtitle = errorText
    ? escapeHtml(errorText)
    : `Cuenta vinculada${email ? `: ${escapeHtml(email)}` : ""}. Volviendo automáticamente a Cátedra...`;

  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${title} — Cátedra</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background:#0e1621; color:#f3f6f9; display:grid; place-items:center; min-height:100vh; margin:0; padding:24px; }
    .card { max-width:440px; width:100%; background:#17212b; border:1px solid #233242; border-radius:18px; padding:28px 24px; text-align:center; }
    h1 { margin:0 0 8px; font-size:20px; }
    p { margin:0 0 22px; color:#9eb1c3; font-size:14px; line-height:1.5; }
    a.btn { display:inline-block; background:#2aabee; color:#fff; text-decoration:none; font-weight:600; padding:12px 22px; border-radius:22px; }
  </style>
</head>
<body>
  <div class="card">
    <h1>${title}</h1>
    <p>${subtitle}</p>
    <a class="btn" href="${safeDeepLink}">Abrir Cátedra</a>
  </div>
  <script>
    setTimeout(() => { window.location.href = ${JSON.stringify(deepLink)}; }, 350);
  </script>
</body>
</html>`;
}

const server = http.createServer(async (req, res) => {
  try {
    const reqUrl = new URL(req.url, `http://${req.headers.host || "localhost"}`);
    const pathname = reqUrl.pathname;

    // 1. Healthcheck para Render y para el portal
    if (req.method === "GET" && pathname === "/health") {
      return sendJson(res, 200, {
        ok: true,
        service: "catedra-render-bridge",
        oauthConfigured: Boolean(GOOGLE_CLIENT_ID && GOOGLE_CLIENT_SECRET),
        redirectUri: getRedirectUri(req),
      });
    }

    // 2. Portal principal (sirve docs/index.html)
    if (req.method === "GET" && (pathname === "/" || pathname === "/index.html")) {
      if (fs.existsSync(PORTAL_HTML_PATH)) {
        const html = fs.readFileSync(PORTAL_HTML_PATH, "utf8");
        return sendHtml(res, 200, html);
      }
      return sendHtml(res, 200, "<h1>Cátedra Bridge</h1><p>Servicio activo.</p>");
    }

    // 3. Descarga directa del APK
    if (req.method === "GET" && pathname === "/download") {
      res.writeHead(302, { Location: APK_DOWNLOAD_URL });
      return res.end();
    }

    // 4. Inicio OAuth2 Google Drive -> redirige al login oficial de Google
    if (req.method === "GET" && pathname === "/auth/google/start") {
      if (!GOOGLE_CLIENT_ID || !GOOGLE_CLIENT_SECRET) {
        return sendHtml(
          res,
          500,
          renderReturnToAppHtml(
            "catedra://oauth/drive?error=render_sin_variables_google",
            "",
            "Faltan GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET en las variables de entorno de Render."
          )
        );
      }
      const redirectUri = getRedirectUri(req);
      const scopes = [
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/userinfo.email",
      ].join(" ");

      const authUrl = new URL("https://accounts.google.com/o/oauth2/v2/auth");
      authUrl.searchParams.set("client_id", GOOGLE_CLIENT_ID);
      authUrl.searchParams.set("redirect_uri", redirectUri);
      authUrl.searchParams.set("response_type", "code");
      authUrl.searchParams.set("scope", scopes);
      authUrl.searchParams.set("access_type", "offline");
      authUrl.searchParams.set("prompt", "consent");
      authUrl.searchParams.set("include_granted_scopes", "true");

      res.writeHead(302, { Location: authUrl.toString() });
      return res.end();
    }

    // 5. Callback OAuth2 Google Drive -> intercambia code por tokens y vuelve al APK via deep link
    if (req.method === "GET" && pathname === "/auth/google/callback") {
      const oauthError = reqUrl.searchParams.get("error");
      if (oauthError) {
        const deep = `catedra://oauth/drive?error=${encodeURIComponent(oauthError)}`;
        return sendHtml(res, 200, renderReturnToAppHtml(deep, "", `Google devolvió: ${oauthError}`));
      }

      const code = reqUrl.searchParams.get("code");
      if (!code) {
        return sendHtml(
          res,
          400,
          renderReturnToAppHtml("catedra://oauth/drive?error=missing_code", "", "Falta el parámetro code de Google.")
        );
      }

      const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          code,
          client_id: GOOGLE_CLIENT_ID,
          client_secret: GOOGLE_CLIENT_SECRET,
          redirect_uri: getRedirectUri(req),
          grant_type: "authorization_code",
        }),
      });

      const tokenJson = await tokenRes.json();
      if (!tokenRes.ok || !tokenJson.access_token) {
        const msg = tokenJson.error_description || tokenJson.error || "token_exchange_failed";
        const deep = `catedra://oauth/drive?error=${encodeURIComponent(msg)}`;
        return sendHtml(res, 200, renderReturnToAppHtml(deep, "", msg));
      }

      let email = "";
      try {
        const uRes = await fetch("https://www.googleapis.com/oauth2/v2/userinfo", {
          headers: { Authorization: `Bearer ${tokenJson.access_token}` },
        });
        if (uRes.ok) {
          const uJson = await uRes.json();
          email = uJson.email || "";
        }
      } catch (_) {}

      const params = new URLSearchParams();
      params.set("access_token", tokenJson.access_token);
      if (tokenJson.refresh_token) params.set("refresh_token", tokenJson.refresh_token);
      params.set("expires_in", String(tokenJson.expires_in || 3500));
      if (email) params.set("email", email);

      const deepLink = `catedra://oauth/drive?${params.toString()}`;
      return sendHtml(res, 200, renderReturnToAppHtml(deepLink, email, ""));
    }

    // 6. Auto-renovación segura de access_token usando refresh_token (Client Secret nunca sale de Render)
    if (req.method === "POST" && pathname === "/auth/google/refresh") {
      if (!GOOGLE_CLIENT_ID || !GOOGLE_CLIENT_SECRET) {
        return sendJson(res, 500, { error: "Google OAuth no configurado en Render." });
      }
      const raw = await readRequestBody(req);
      const parsed = JSON.parse(raw || "{}");
      const refreshToken = String(parsed.refresh_token || "").trim();
      if (!refreshToken) {
        return sendJson(res, 400, { error: "refresh_token requerido" });
      }

      const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          refresh_token: refreshToken,
          client_id: GOOGLE_CLIENT_ID,
          client_secret: GOOGLE_CLIENT_SECRET,
          grant_type: "refresh_token",
        }),
      });
      const tokenJson = await tokenRes.json();
      if (!tokenRes.ok || !tokenJson.access_token) {
        return sendJson(res, 401, { error: tokenJson.error_description || tokenJson.error || "refresh_failed" });
      }
      return sendJson(res, 200, {
        access_token: tokenJson.access_token,
        expires_in: tokenJson.expires_in || 3500,
      });
    }

    sendJson(res, 404, { error: "Not found" });
  } catch (err) {
    sendJson(res, 500, { error: err.message || "Internal error" });
  }
});

server.listen(PORT, () => {
  console.log(`Catedra Render Bridge listening on port ${PORT}`);
});


