'use strict';

const API = '/api/v1';
const $ = (id) => document.getElementById(id);
const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dt = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

const state = {
  token: sessionStorage.getItem('token'),
  userName: sessionStorage.getItem('userName') || '',
  accounts: [],
  selected: null,
  op: 'deposit',
  page: 0,
  totalPages: 0,
};

async function api(path, { method = 'GET', body, idemKey } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (state.token) headers.Authorization = 'Bearer ' + state.token;
  if (idemKey) headers['Idempotency-Key'] = idemKey;
  const res = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    if (res.status === 401 && state.token) { logout(); }
    const err = new Error(describeError(data, res.status));
    err.status = res.status;
    throw err;
  }
  return data;
}

function describeError(data, status) {
  if (!data) return 'Erro ' + status;
  if (data.errors && typeof data.errors === 'object') {
    const msgs = Array.isArray(data.errors)
      ? data.errors.map((e) => e.message || e.defaultMessage || JSON.stringify(e))
      : Object.entries(data.errors).map(([k, v]) => k + ': ' + v);
    if (msgs.length) return msgs.join(' · ');
  }
  return data.detail || data.title || 'Erro ' + status;
}

let toastTimer;
function toast(msg, kind = '') {
  const t = $('toast');
  t.textContent = msg;
  t.className = 'toast ' + kind;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 4500);
}

async function guarded(fn, button) {
  if (button) button.disabled = true;
  try { await fn(); }
  catch (e) { toast(e.message, 'error'); }
  finally { if (button) button.disabled = false; }
}

function showAuthTab(login) {
  $('loginForm').hidden = !login;
  $('registerForm').hidden = login;
  $('tabLogin').classList.toggle('active', login);
  $('tabRegister').classList.toggle('active', !login);
}
$('tabLogin').onclick = () => showAuthTab(true);
$('tabRegister').onclick = () => showAuthTab(false);

async function doLogin(email, password) {
  const tk = await api('/auth/login', { method: 'POST', body: { email, password } });
  state.token = tk.accessToken;
  sessionStorage.setItem('token', state.token);
}

$('loginForm').onsubmit = (ev) => {
  ev.preventDefault();
  const f = new FormData(ev.target);
  guarded(async () => {
    await doLogin(f.get('email'), f.get('password'));
    state.userName = f.get('email');
    sessionStorage.setItem('userName', state.userName);
    await enterApp();
  }, ev.submitter);
};

$('registerForm').onsubmit = (ev) => {
  ev.preventDefault();
  const f = new FormData(ev.target);
  guarded(async () => {
    const c = await api('/auth/register', {
      method: 'POST',
      body: { name: f.get('name'), email: f.get('email'), password: f.get('password'), cpf: f.get('cpf') },
    });
    await doLogin(f.get('email'), f.get('password'));
    state.userName = c.name;
    sessionStorage.setItem('userName', state.userName);
    toast('Cadastro realizado!', 'ok');
    await enterApp();
  }, ev.submitter);
};

function logout() {
  state.token = null; state.accounts = []; state.selected = null;
  sessionStorage.clear();
  $('appView').hidden = true; $('authView').hidden = false; $('userBox').hidden = true;
}
$('logoutBtn').onclick = logout;

async function enterApp() {
  $('authView').hidden = true; $('appView').hidden = false; $('userBox').hidden = false;
  $('userName').textContent = state.userName;
  await loadAccounts();
}

async function loadAccounts() {
  state.accounts = await api('/accounts');
  renderAccounts();
  if (state.accounts.length && !state.accounts.some((a) => a.id === state.selected)) {
    selectAccount(state.accounts[0].id);
  } else if (state.selected) {
    selectAccount(state.selected);
  } else {
    $('opsCard').hidden = true; $('stmtCard').hidden = true;
  }
}

function renderAccounts() {
  const ul = $('accountList');
  ul.replaceChildren();
  $('noAccounts').hidden = state.accounts.length > 0;
  for (const a of state.accounts) {
    const li = document.createElement('li');
    if (a.id === state.selected) li.className = 'sel';
    const left = document.createElement('div');
    const t = document.createElement('div'); t.className = 'acc-title';
    t.textContent = (a.type === 'CORRENTE' ? 'Conta corrente' : 'Poupança') + ' #' + a.id;
    const s = document.createElement('div'); s.className = 'acc-sub';
    s.textContent = 'Ag ' + a.agency + ' · Nº ' + a.number + ' · ' + a.status;
    left.append(t, s);
    const b = document.createElement('div'); b.className = 'acc-bal'; b.textContent = brl.format(a.balance);
    li.append(left, b);
    li.onclick = () => selectAccount(a.id);
    ul.append(li);
  }
}

$('openAccountBtn').onclick = (ev) => guarded(async () => {
  const acc = await api('/accounts', { method: 'POST', body: { type: $('newType').value } });
  state.selected = acc.id;
  toast('Conta #' + acc.id + ' aberta', 'ok');
  await loadAccounts();
}, ev.currentTarget);

async function selectAccount(id) {
  state.selected = id;
  state.page = 0;
  renderAccounts();
  $('opsCard').hidden = false; $('stmtCard').hidden = false;
  $('selLabel').textContent = '— conta #' + id;
  $('reconcileResult').textContent = '';
  await Promise.all([refreshBalance(), loadStatement()]).catch((e) => toast(e.message, 'error'));
}

async function refreshBalance() {
  const b = await api('/accounts/' + state.selected + '/balance');
  $('balanceValue').textContent = brl.format(b.availableBalance);
  const acc = state.accounts.find((a) => a.id === state.selected);
  if (acc) { acc.balance = b.balance; renderAccounts(); }
}

const OPS = {
  deposit: { label: 'Depositar', done: 'Depósito realizado' },
  withdraw: { label: 'Sacar', done: 'Saque realizado' },
  transfer: { label: 'Transferir', done: 'Transferência realizada' },
};

document.querySelectorAll('[data-op]').forEach((btn) => {
  btn.onclick = () => {
    state.op = btn.dataset.op;
    document.querySelectorAll('[data-op]').forEach((b) => b.classList.toggle('active', b === btn));
    $('targetRow').hidden = state.op !== 'transfer';
    $('opForm').elements.target.required = state.op === 'transfer';
    $('opSubmit').textContent = OPS[state.op].label;
  };
});

$('opForm').onsubmit = (ev) => {
  ev.preventDefault();
  const f = new FormData(ev.target);
  const amount = Number(f.get('amount'));
  const description = (f.get('description') || '').trim() || undefined;
  // uma chave NOVA por envio; um retry de rede reenviaria a mesma chave e não duplicaria
  const idemKey = crypto.randomUUID();
  guarded(async () => {
    const id = state.selected;
    if (state.op === 'deposit') {
      await api('/accounts/' + id + '/deposits', { method: 'POST', idemKey, body: { amount, description } });
    } else if (state.op === 'withdraw') {
      await api('/accounts/' + id + '/withdrawals', { method: 'POST', idemKey, body: { amount, description } });
    } else {
      await api('/transfers', {
        method: 'POST', idemKey,
        body: { sourceAccountId: id, targetAccountId: Number(f.get('target')), amount, description },
      });
    }
    toast(OPS[state.op].done, 'ok');
    ev.target.reset();
    state.page = 0;
    await loadAccounts();
  }, ev.submitter);
};

$('reconcileBtn').onclick = (ev) => guarded(async () => {
  const r = await api('/accounts/' + state.selected + '/reconciliation');
  $('reconcileResult').textContent = (r.consistent ? '✔ Consistente' : '✖ INCONSISTENTE')
    + ' — saldo gravado ' + brl.format(r.storedBalance) + ' · saldo pelo ledger ' + brl.format(r.ledgerBalance);
}, ev.currentTarget);

async function loadStatement() {
  const q = new URLSearchParams({ page: state.page, size: 10 });
  if ($('stFrom').value) q.set('from', $('stFrom').value);
  if ($('stTo').value) q.set('to', $('stTo').value);
  const p = await api('/accounts/' + state.selected + '/statement?' + q);
  state.totalPages = p.totalPages;
  const body = $('stBody');
  body.replaceChildren();
  $('stEmpty').hidden = p.content.length > 0;
  const types = { DEPOSIT: 'Depósito', WITHDRAWAL: 'Saque', TRANSFER: 'Transferência', REVERSAL: 'Estorno' };
  for (const e of p.content) {
    const tr = document.createElement('tr');
    const cells = [dt.format(new Date(e.createdAt)), types[e.type] || e.type, e.description || '—'];
    for (const c of cells) { const td = document.createElement('td'); td.textContent = c; tr.append(td); }
    const credit = e.direction === 'CREDIT';
    const v = document.createElement('td');
    v.className = 'num ' + (credit ? 'credit' : 'debit');
    v.textContent = (credit ? '+ ' : '− ') + brl.format(e.amount);
    const bal = document.createElement('td'); bal.className = 'num'; bal.textContent = brl.format(e.balanceAfter);
    tr.append(v, bal);
    body.append(tr);
  }
  $('pageInfo').textContent = p.totalPages ? 'Página ' + (p.page + 1) + ' de ' + p.totalPages : '';
  $('prevPage').disabled = p.page <= 0;
  $('nextPage').disabled = p.page + 1 >= p.totalPages;
}

$('stFilter').onclick = (ev) => { state.page = 0; guarded(loadStatement, ev.currentTarget); };
$('prevPage').onclick = (ev) => { if (state.page > 0) { state.page--; guarded(loadStatement, ev.currentTarget); } };
$('nextPage').onclick = (ev) => { if (state.page + 1 < state.totalPages) { state.page++; guarded(loadStatement, ev.currentTarget); } };

if (state.token) { enterApp().catch(() => logout()); }
