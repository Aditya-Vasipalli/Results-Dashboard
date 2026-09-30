// One command for the whole app.
//   npm run dev        Spring Boot API + Vite with hot reload, opens the browser
//   npm start          builds React into Spring Boot and runs a single server
//   npm test           backend tests
//   npm run reset-db   like dev, but wipes the database and reloads the demo data
// Ports are picked automatically: 8080 / 5173 if free, otherwise the next free ones.
import { spawn, spawnSync } from 'node:child_process';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const backend = path.join(root, 'backend');
const client = path.join(root, 'client');
const viteBin = path.join(root, 'node_modules', 'vite', 'bin', 'vite.js');
const isWin = process.platform === 'win32';

const flags = new Set(process.argv.slice(2));
const mode = flags.has('--prod') ? 'prod' : flags.has('--test') ? 'test' : 'dev';
const reset = flags.has('--reset');

const tty = process.stdout.isTTY;
const paint = (code) => (s) => (tty ? `\x1b[${code}m${s}\x1b[0m` : String(s));
const bold = paint('1'), dim = paint('2'), red = paint('31'), green = paint('32'), yellow = paint('33'), blue = paint('34'), magenta = paint('35');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ------------------------------------------------------------------ processes

const children = [];
let stopping = false;

function stopAll() {
  stopping = true;
  for (const ch of children) {
    if (ch.exitCode !== null) continue;
    // On Windows the child is cmd.exe running mvnw.cmd; /T also stops the java process under it.
    if (isWin) spawnSync('taskkill', ['/pid', String(ch.pid), '/T', '/F'], { stdio: 'ignore' });
    else ch.kill('SIGTERM');
  }
}

function fail(message) {
  console.error(`\n${red('✖')} ${message}\n`);
  stopAll();
  process.exit(1);
}

process.on('SIGINT', () => { stopAll(); process.exit(0); });
process.on('SIGTERM', () => { stopAll(); process.exit(0); });
process.on('exit', () => { if (!stopping) stopAll(); });

/** Prefixes every output line of a child with a label such as [api]. */
function relay(stream, label) {
  let buf = '';
  stream.setEncoding('utf8');
  stream.on('data', (chunk) => {
    buf += chunk;
    const lines = buf.split(/\r?\n/);
    buf = lines.pop();
    for (const line of lines) if (line.trim()) console.log(`${label} ${line}`);
  });
  stream.on('end', () => { if (buf.trim()) console.log(`${label} ${buf}`); });
}

function run(label, command, args, options) {
  const ch = spawn(command, args, { ...options, stdio: ['ignore', 'pipe', 'pipe'] });
  relay(ch.stdout, label);
  relay(ch.stderr, label);
  children.push(ch);
  return ch;
}

/** Starts the Maven wrapper in backend/. Arguments must not contain spaces. */
function maven(args, { label, env, inherit = false }) {
  const options = { cwd: backend, env };
  let ch;
  if (isWin) {
    // .cmd needs a shell, and some Windows setups (NoDefaultCurrentDirectoryInExePath)
    // refuse to run programs from the current folder, so use the full path.
    const command = [`"${path.join(backend, 'mvnw.cmd')}"`, ...args].join(' ');
    ch = inherit ? spawn(command, { ...options, shell: true, stdio: 'inherit' }) : run(label, command, [], { ...options, shell: true });
  } else {
    const command = path.join(backend, 'mvnw');
    ch = inherit ? spawn(command, args, { ...options, stdio: 'inherit' }) : run(label, command, args, options);
  }
  if (inherit) children.push(ch);
  return ch;
}

// ----------------------------------------------------------------------- ports

function somethingListening(port, host) {
  return new Promise((resolve) => {
    const s = net.connect({ port, host });
    s.setTimeout(400);
    s.once('connect', () => { s.destroy(); resolve(true); });
    s.once('timeout', () => { s.destroy(); resolve(false); });
    s.once('error', () => resolve(false));
  });
}

function canBind(port) {
  return new Promise((resolve) => {
    const srv = net.createServer();
    srv.once('error', () => resolve(false));
    srv.once('listening', () => srv.close(() => resolve(true)));
    srv.listen(port);
  });
}

async function freePort(preferred) {
  for (let p = preferred; p < preferred + 50; p++) {
    if (await somethingListening(p, '127.0.0.1')) continue;
    if (await somethingListening(p, '::1')) continue;
    if (await canBind(p)) return p;
  }
  fail(`Could not find a free port between ${preferred} and ${preferred + 49}.`);
}

async function waitForHttp(url, child, timeoutMs) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (child.exitCode !== null) return false;
    try {
      await fetch(url, { signal: AbortSignal.timeout(2000) });
      return true;
    } catch {
      await sleep(700);
    }
  }
  return false;
}

// -------------------------------------------------------------- prerequisites

function checkJava() {
  const javaHome = process.env.JAVA_HOME;
  const java = javaHome ? path.join(javaHome, 'bin', isWin ? 'java.exe' : 'java') : 'java';
  const r = spawnSync(java, ['-version'], { encoding: 'utf8' });
  if (r.error) {
    fail(javaHome
      ? `JAVA_HOME points to ${javaHome}, but there is no working Java there. Fix JAVA_HOME or remove it.`
      : 'Java is not installed or not on PATH. Install JDK 21 or newer (e.g. Eclipse Temurin 21).');
  }
  const text = `${r.stderr}${r.stdout}`;
  const m = text.match(/version "(\d+)(?:\.(\d+))?/);
  const major = m ? (m[1] === '1' ? Number(m[2]) : Number(m[1])) : 0;
  if (major < 21) fail(`Java 21 or newer is required, but found: ${text.split(/\r?\n/)[0] || 'unknown version'}`);
  console.log(`${green('✔')} Java ${major}`);
}

function databaseConfig() {
  const url = process.env.DB_URL || 'jdbc:postgresql://localhost:5432/vit_results';
  const m = url.match(/^jdbc:postgresql:\/\/([^:/?]+)(?::(\d+))?\/([^?]+)/);
  if (!m) return null;
  return {
    host: m[1],
    port: Number(m[2] || 5432),
    database: m[3],
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || '1234',
  };
}

/** Connects to PostgreSQL and creates the database if it does not exist yet. */
async function ensureDatabase(name) {
  const cfg = databaseConfig();
  if (!cfg) return; // unusual DB_URL; let Spring Boot report any problem
  const db = new pg.Client({ ...cfg, database: 'postgres', connectionTimeoutMillis: 5000 });
  try {
    await db.connect();
  } catch (e) {
    const where = `${cfg.host}:${cfg.port}`;
    if (e.code === 'ECONNREFUSED' || e.code === 'ETIMEDOUT' || /timeout/i.test(e.message)) {
      fail(`Cannot reach PostgreSQL at ${where}.\n  Start it: press Win+R, type services.msc, find "postgresql-x64-…" and click Start.`);
    }
    if (e.code === '28P01' || e.code === '28000') {
      fail(`PostgreSQL rejected user "${cfg.user}" with password "${cfg.password}".\n  Set the right one before running, e.g. in PowerShell:  $env:DB_PASSWORD = "yourpassword"`);
    }
    fail(`Could not connect to PostgreSQL at ${where}: ${e.message}`);
  }
  try {
    const { rowCount } = await db.query('SELECT 1 FROM pg_database WHERE datname = $1', [name]);
    if (rowCount === 0) {
      await db.query(`CREATE DATABASE "${name.replace(/"/g, '""')}"`);
      console.log(`${green('✔')} Created database ${name}`);
    } else {
      console.log(`${green('✔')} PostgreSQL · database ${name}`);
    }
  } catch (e) {
    fail(`Could not create database ${name}: ${e.message}`);
  } finally {
    await db.end().catch(() => {});
  }
}

function openBrowser(url) {
  if (process.env.BROWSER === 'none') return;
  const [cmd, args] = isWin ? ['cmd', ['/c', 'start', '', url]]
    : process.platform === 'darwin' ? ['open', [url]] : ['xdg-open', [url]];
  try {
    spawn(cmd, args, { stdio: 'ignore', detached: true }).unref();
  } catch { /* opening the browser is a convenience only */ }
}

// ------------------------------------------------------------------------ main

console.log(`\n${bold('Result Desk')} ${dim(mode === 'prod' ? '· single server' : mode === 'test' ? '· tests' : '· dev mode')}\n`);
checkJava();

if (mode === 'test') {
  await ensureDatabase('vit_results_test');
  const t = maven(['test'], { env: process.env, inherit: true });
  t.on('exit', (code) => process.exit(code ?? 1));
} else {
  await ensureDatabase(databaseConfig()?.database ?? 'vit_results');

  if (mode === 'prod') {
    console.log(dim('Building the React app…'));
    const b = spawnSync(process.execPath, [viteBin, 'build'], { cwd: client, stdio: 'inherit' });
    if (b.status !== 0) fail('The React build failed (see above).');
  }

  const apiPort = await freePort(8080);
  if (apiPort !== 8080) console.log(`${yellow('!')} Port 8080 is busy, so the API will use ${apiPort}`);

  const api = maven(['-q', 'spring-boot:run', ...(reset ? ['-Dspring-boot.run.arguments=--app.reset=true'] : [])], {
    label: blue('[api]'),
    env: {
      ...process.env,
      PORT: String(apiPort),
      SPRING_MAIN_BANNER_MODE: 'off',
      LOGGING_LEVEL_ROOT: 'WARN',
      LOGGING_LEVEL_EDU_VIT_RESULTS: 'INFO',
    },
  });
  api.on('exit', (code) => {
    if (!stopping) fail(`The backend stopped (exit code ${code}). The [api] lines above say why.`);
  });

  console.log(dim(`Starting the Spring Boot API on port ${apiPort}… (the first run can take a minute)`));
  if (!(await waitForHttp(`http://127.0.0.1:${apiPort}/api/auth/me`, api, 5 * 60_000))) {
    fail('The backend did not start. The [api] lines above say why.');
  }

  let url = `http://localhost:${apiPort}`;
  if (mode === 'dev') {
    const webPort = await freePort(5173);
    const web = run(magenta('[web]'), process.execPath,
      ['--max-http-header-size=65536', viteBin, '--port', String(webPort), '--strictPort'],
      { cwd: client, env: { ...process.env, API_PORT: String(apiPort) } });
    web.on('exit', (code) => {
      if (!stopping) fail(`The Vite dev server stopped (exit code ${code}). The [web] lines above say why.`);
    });
    if (!(await waitForHttp(`http://localhost:${webPort}/`, web, 60_000))) fail('The Vite dev server did not start.');
    url = `http://localhost:${webPort}`;
  }

  console.log(`
${green('✔')} ${bold('Ready:')} open ${bold(url)}
  API on http://localhost:${apiPort}${mode === 'dev' ? ' · edits to client/src reload instantly' : ''}
  Demo logins: admin@vit.edu / admin123 · iyer@vit.edu / teacher123 · aarav.mehta@vit.edu / student123
  Press Ctrl+C to stop.
`);
  openBrowser(url);
}
