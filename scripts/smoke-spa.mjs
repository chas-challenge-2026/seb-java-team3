// Rök-koll för #162: att appen servar React-appen på frontend-routes och att /api
// fortfarande svarar som API. Körs mot en app som redan är igång, lokalt eller på stage:
//
//   node scripts/smoke-spa.mjs                        (standard: http://localhost:8084)
//   node scripts/smoke-spa.mjs https://<stage-adress>
//
// Med inloggning körs även kontrollerna som kräver token (t.ex. en seed-användare ur README):
//   bash:       SMOKE_EMAIL=... SMOKE_PASSWORD=... node scripts/smoke-spa.mjs
//   PowerShell: $env:SMOKE_EMAIL="..."; $env:SMOKE_PASSWORD="..."; node scripts/smoke-spa.mjs
//
// Kräver bara Node 18+ (inbyggd fetch). Avslutas med felkod 1 om någon kontroll misslyckas.

const base = (process.argv[2] ?? "http://localhost:8084").replace(/\/+$/, "");
let failures = 0;

function check(name, ok, detail) {
  console.log(`${ok ? "OK " : "FEL"}  ${name}${ok ? "" : `  (${detail})`}`);
  if (!ok) failures++;
}

async function get(path, headers = {}) {
  const res = await fetch(base + path, { headers, redirect: "manual" });
  return { status: res.status, type: res.headers.get("content-type") ?? "", body: await res.text() };
}

const isReactApp = (r) => r.status === 200 && r.type.includes("text/html") && r.body.includes('<div id="root">');
const describe = (r) => `status ${r.status}, ${r.type || "ingen content-type"}`;

console.log(`Rök-koll mot ${base}\n`);

try {
  await fetch(base + "/", { redirect: "manual" });
} catch (error) {
  console.log(`FEL  Kunde inte nå ${base}. Är appen igång? (${error.cause?.code ?? error.message})`);
  process.exit(1);
}

// 1. Frontend-routes ska ge React-appen, även vid direktlänk eller omladdning.
for (const path of ["/", "/login", "/attest", "/payments/new", "/my-payments", "/audit", "/finns-inte"]) {
  const r = await get(path);
  check(`GET ${path} ger React-appen`, isReactApp(r), describe(r));
}

// 2. Filerna som index.html pekar på ska serveras som filer, inte som index.html.
const index = await get("/");
const files = [...index.body.matchAll(/(?:src|href)="(\/[^"]+\.[a-z0-9]+)"/g)].map((m) => m[1]);
check("index.html pekar på byggda filer", files.some((f) => f.startsWith("/assets/")), "inga /assets/-länkar hittades");
for (const path of files) {
  const r = await get(path);
  check(`GET ${path} är en fil`, r.status === 200 && !r.type.includes("text/html"), describe(r));
}
const missing = await get("/assets/finns-inte.js");
check("GET /assets/finns-inte.js ger 404, inte React-appen", missing.status === 404 && !isReactApp(missing), describe(missing));

// 3. /api ska svara som API, aldrig med index.html.
for (const path of ["/api/auth/me", "/api/finns-inte"]) {
  const r = await get(path);
  check(`GET ${path} utan token ger 401`, r.status === 401 && !r.type.includes("text/html"), describe(r));
}

const { SMOKE_EMAIL: email, SMOKE_PASSWORD: password } = process.env;
if (email && password) {
  const login = await fetch(`${base}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const token = login.ok ? (await login.json()).token : null;
  check("POST /api/auth/login ger en token", Boolean(token), `status ${login.status}`);

  if (token) {
    const auth = { Authorization: `Bearer ${token}` };
    const me = await get("/api/auth/me", auth);
    check("GET /api/auth/me med token ger JSON", me.status === 200 && me.type.includes("json"), describe(me));
    // Med token släpps requesten förbi säkerheten. Då visar 404 att fallbacken inte tog den.
    for (const path of ["/api/finns-inte", "/api"]) {
      const r = await get(path, auth);
      check(`GET ${path} med token ger 404, inte React-appen`, r.status === 404 && !isReactApp(r), describe(r));
    }
  }
} else {
  console.log("\n(Hoppar över kontrollerna med token: sätt SMOKE_EMAIL och SMOKE_PASSWORD)");
}

console.log(failures === 0 ? "\nAlla kontroller OK" : `\n${failures} kontroll(er) misslyckades`);
process.exit(failures === 0 ? 0 : 1);
