// APEX VPN — панель управления: Telegram-бот + сервер обновлений на Cloudflare Workers.
//
// Для приложения:
//   GET  /update.json      опубликованное обновление
//   GET  /apk/<code>       APK выбранной сборки (хранится в KV частями)
//   GET  /news.json        объявления и техработы
//   POST /crash            отчёт о сбое (text/plain) → админам в Telegram
//   POST /ping             {id, v, sdk} — статистика: сколько устройств и каких версий
//   POST /feedback         {text, contact, v, model} — «Сообщить о проблеме» → админам
// Для сборки (GitHub Actions):
//   POST /ci/build         новая сборка готова → бот присылает кнопки «Опубликовать»
// Для Telegram:
//   POST /tg               вебхук бота
//
// Переменные окружения (секреты Worker): BOT_TOKEN, ADMIN_IDS (через запятую), CI_SECRET.
// Хранилище: KV-привязка APEX.

const KEEP_BUILDS = 3

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url)
    const path = url.pathname
    try {
      if (request.method === 'GET' && path === '/') return text('APEX VPN panel')
      if (request.method === 'GET' && path === '/update.json') return getUpdate(env, url)
      if (request.method === 'GET' && path.startsWith('/apk/')) return getApk(env, path.slice(5), ctx)
      if (request.method === 'GET' && path === '/news.json') return json(await getNews(env))
      if (request.method === 'POST' && path === '/crash') return postCrash(request, env)
      if (request.method === 'POST' && path === '/ping') return postPing(request, env)
      if (request.method === 'POST' && path === '/feedback') return postFeedback(request, env)
      if (request.method === 'POST' && path === '/ci/build') return postBuild(request, env)
      if (request.method === 'POST' && path === '/tg') return telegram(request, env)
      return text('not found', 404)
    } catch (e) {
      return text('error: ' + (e && e.message), 500)
    }
  },
}

// ─── Ответы ─────────────────────────────────────────────────────────────

function text(body, status = 200) {
  return new Response(body, { status, headers: { 'content-type': 'text/plain; charset=utf-8' } })
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  })
}

async function kvJson(env, key, fallback) {
  const v = await env.APEX.get(key, 'json')
  return v == null ? fallback : v
}

function admins(env) {
  return String(env.ADMIN_IDS || '').split(/[,\s]+/).filter(Boolean)
}

function escapeHtml(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function formatSize(bytes) {
  return (bytes / 1048576).toFixed(1) + ' МБ'
}

// ─── Обновления ─────────────────────────────────────────────────────────

async function getUpdate(env, url) {
  const pub = await kvJson(env, 'update', null)
  if (!pub) return json({ versionCode: 0, versionName: '', apkUrl: '', changelog: '' })
  const build = await kvJson(env, 'build:' + pub.versionCode, null)
  if (!build) return json({ versionCode: 0, versionName: '', apkUrl: '', changelog: '' })
  return json({
    versionCode: build.versionCode,
    versionName: build.versionName,
    apkUrl: `${url.origin}/apk/${build.versionCode}`,
    sha256: build.sha256,
    size: build.size,
    changelog: build.changelog,
    mandatory: !!pub.mandatory,
    minSupportedVersionCode: pub.minSupportedVersionCode || 0,
  })
}

// APK хранится в KV частями до 20 МБ (ограничение KV — 25 МБ на значение)
async function getApk(env, code, ctx) {
  code = code.replace(/\.apk$/, '')
  const build = await kvJson(env, 'build:' + code, null)
  if (!build) return text('no such build', 404)
  // FixedLengthStream — чтобы у загрузки был размер (прогресс в приложении)
  const { readable, writable } = new FixedLengthStream(build.size)
  const pump = async () => {
    const writer = writable.getWriter()
    try {
      for (let i = 0; i < build.parts; i++) {
        const part = await env.APEX.get(`apk:${code}:${i}`, 'arrayBuffer')
        if (!part) throw new Error('missing part ' + i)
        await writer.write(new Uint8Array(part))
      }
      await writer.close()
    } catch (e) {
      await writer.abort(e)
    }
  }
  ctx.waitUntil(pump())
  ctx.waitUntil(bump(env, 'downloads:' + code))
  return new Response(readable, {
    headers: {
      'content-type': 'application/vnd.android.package-archive',
      'content-disposition': `attachment; filename="apex-vpn-${build.versionName}.apk"`,
    },
  })
}

async function bump(env, key) {
  const n = parseInt((await env.APEX.get(key)) || '0', 10)
  await env.APEX.put(key, String(n + 1))
}

function authorized(request, env) {
  const h = request.headers.get('authorization') || ''
  return env.CI_SECRET && h === 'Bearer ' + env.CI_SECRET
}

async function postBuild(request, env) {
  if (!authorized(request, env)) return text('forbidden', 403)
  const b = await request.json()
  const code = parseInt(b.versionCode, 10)
  if (!code || !b.parts) return text('bad build', 400)
  const build = {
    versionCode: code,
    versionName: String(b.versionName || code),
    sha256: String(b.sha256 || ''),
    size: parseInt(b.size, 10) || 0,
    parts: parseInt(b.parts, 10),
    changelog: String(b.changelog || '').slice(0, 3000),
    runUrl: String(b.runUrl || ''),
    at: Date.now(),
  }
  await env.APEX.put('build:' + code, JSON.stringify(build))
  const list = (await kvJson(env, 'builds', [])).filter((c) => c !== code)
  list.unshift(code)
  // Старые сборки удаляем, но опубликованную оставляем
  const pub = await kvJson(env, 'update', null)
  const keep = list.slice(0, KEEP_BUILDS)
  for (const old of list.slice(KEEP_BUILDS)) {
    if (pub && pub.versionCode === old) { keep.push(old); continue }
    const ob = await kvJson(env, 'build:' + old, null)
    if (ob) for (let i = 0; i < ob.parts; i++) await env.APEX.delete(`apk:${old}:${i}`)
    await env.APEX.delete('build:' + old)
  }
  await env.APEX.put('builds', JSON.stringify(keep))

  const textMsg =
    `📦 <b>Новая сборка ${escapeHtml(build.versionName)}</b> (код ${code}, ${formatSize(build.size)})\n\n` +
    `${escapeHtml(build.changelog) || 'без описания'}\n\n` +
    (pub ? `Сейчас у пользователей: ${escapeHtml(pub.versionName)}\n` : 'Обновления ещё не публиковались\n') +
    'Опубликовать для всех?'
  await notifyAdmins(env, textMsg, publishKeyboard(code))
  return json({ ok: true })
}

function publishKeyboard(code) {
  return {
    inline_keyboard: [
      [{ text: '🚀 Опубликовать', callback_data: 'pub:' + code }],
      [{ text: '⚠️ Обязательное обновление', callback_data: 'pubm:' + code }],
      [{ text: '✖️ Пропустить', callback_data: 'skip:' + code }],
    ],
  }
}

async function publish(env, code, mandatory) {
  const build = await kvJson(env, 'build:' + code, null)
  if (!build) return 'Сборка не найдена — возможно, уже удалена'
  await env.APEX.put('update', JSON.stringify({
    versionCode: build.versionCode,
    versionName: build.versionName,
    mandatory,
    minSupportedVersionCode: mandatory ? build.versionCode : 0,
    at: Date.now(),
  }))
  return `✅ Версия ${build.versionName} опубликована${mandatory ? ' как обязательная' : ''}. Приложения увидят её при следующем запуске.`
}

// ─── Объявления ─────────────────────────────────────────────────────────

async function getNews(env) {
  const out = []
  const maint = await kvJson(env, 'maintenance', null)
  if (maint) out.push({ id: 'maint-' + maint.at, title: '🛠 Технические работы', text: maint.text, level: 'warning' })
  const news = await kvJson(env, 'news', null)
  if (news) out.push(news)
  return out
}

// ─── От приложения ──────────────────────────────────────────────────────

async function readText(request, max) {
  const t = await request.text()
  return t.slice(0, max)
}

async function postCrash(request, env) {
  const body = await readText(request, 6000)
  if (!body.trim()) return text('empty', 400)
  // Не больше 30 отчётов в час — защита от потока одинаковых сбоев
  const hourKey = 'crashcount:' + Math.floor(Date.now() / 3600000)
  const count = parseInt((await env.APEX.get(hourKey)) || '0', 10)
  await env.APEX.put(hourKey, String(count + 1), { expirationTtl: 7200 })
  const recent = await kvJson(env, 'crashes', [])
  recent.unshift({ at: Date.now(), text: body.slice(0, 1500) })
  await env.APEX.put('crashes', JSON.stringify(recent.slice(0, 20)))
  if (count < 30) {
    await notifyAdmins(env, '🐞 <b>Сбой в приложении</b>\n<pre>' + escapeHtml(body.slice(0, 3500)) + '</pre>')
  }
  return json({ ok: true })
}

async function postPing(request, env) {
  const b = await request.json().catch(() => ({}))
  const id = String(b.id || '').replace(/[^a-f0-9]/gi, '').slice(0, 32)
  if (id.length < 8) return text('bad id', 400)
  await env.APEX.put('dev:' + id, JSON.stringify({ v: String(b.v || '?').slice(0, 20), sdk: b.sdk | 0, t: Date.now() }), {
    expirationTtl: 60 * 60 * 24 * 30,
  })
  return json({ ok: true })
}

async function postFeedback(request, env) {
  const b = await request.json().catch(() => ({}))
  const msg = String(b.text || '').trim().slice(0, 2000)
  if (!msg) return text('empty', 400)
  const fromKey = 'fbcount:' + Math.floor(Date.now() / 3600000)
  const count = parseInt((await env.APEX.get(fromKey)) || '0', 10)
  if (count > 50) return text('too many', 429)
  await env.APEX.put(fromKey, String(count + 1), { expirationTtl: 7200 })
  await notifyAdmins(env,
    '✉️ <b>Сообщение из приложения</b>\n\n' + escapeHtml(msg) + '\n\n' +
    `<i>Версия ${escapeHtml(b.v)}, ${escapeHtml(b.model)}, Android ${escapeHtml(b.android)}` +
    (b.contact ? `\nКонтакт: ${escapeHtml(String(b.contact).slice(0, 100))}` : '') + '</i>')
  return json({ ok: true })
}

// ─── Telegram ───────────────────────────────────────────────────────────

async function tg(env, method, payload) {
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return r.json().catch(() => ({}))
}

async function notifyAdmins(env, html, keyboard) {
  for (const id of admins(env)) {
    await tg(env, 'sendMessage', {
      chat_id: id, text: html, parse_mode: 'HTML', disable_web_page_preview: true,
      ...(keyboard ? { reply_markup: keyboard } : {}),
    })
  }
}

const MENU = {
  inline_keyboard: [
    [{ text: '📦 Обновления', callback_data: 'm:update' }, { text: '📊 Статистика', callback_data: 'm:stats' }],
    [{ text: '📢 Объявление', callback_data: 'm:news' }, { text: '🛠 Техработы', callback_data: 'm:maint' }],
    [{ text: '🐞 Сбои', callback_data: 'm:crashes' }, { text: '❓ Помощь', callback_data: 'm:help' }],
  ],
}

const HELP =
  '<b>Панель APEX VPN</b>\n\n' +
  '📦 /update — опубликованная версия и последние сборки\n' +
  '📢 /news <i>текст</i> — объявление в приложении (первая строка — заголовок)\n' +
  '⚠️ /warn <i>текст</i> — важное объявление (жёлтое)\n' +
  '🔕 /news_off — убрать объявление\n' +
  '🛠 /maintenance <i>текст</i> — баннер «Технические работы»\n' +
  '✅ /maintenance_off — убрать баннер\n' +
  '📊 /stats — устройства и версии за 1, 7 и 30 дней\n' +
  '🐞 /crashes — последние сбои\n' +
  '↩️ /unpublish — снять обновление\n\n' +
  'Новые сборки бот присылает сам после каждой сборки на GitHub.'

async function telegram(request, env) {
  // Telegram присылает секрет, заданный при setWebhook
  if (request.headers.get('x-telegram-bot-api-secret-token') !== env.CI_SECRET) return text('forbidden', 403)
  const update = await request.json()
  const msg = update.message
  const cb = update.callback_query
  const from = String((msg && msg.from && msg.from.id) || (cb && cb.from && cb.from.id) || '')
  const chat = (msg && msg.chat.id) || (cb && cb.message && cb.message.chat.id)
  const reply = (html, keyboard) =>
    tg(env, 'sendMessage', { chat_id: chat, text: html, parse_mode: 'HTML', disable_web_page_preview: true, ...(keyboard ? { reply_markup: keyboard } : {}) })

  if (!admins(env).includes(from)) {
    if (msg) await reply(`Это служебный бот APEX VPN. Ваш ID: <code>${from}</code>`)
    return json({ ok: true })
  }

  if (cb) {
    const [action, arg] = String(cb.data || '').split(':')
    let answer = ''
    if (action === 'pub' || action === 'pubm') answer = await publish(env, parseInt(arg, 10), action === 'pubm')
    else if (action === 'skip') answer = 'Сборка пропущена — пользователи её не получат.'
    else if (action === 'm') {
      await tg(env, 'answerCallbackQuery', { callback_query_id: cb.id })
      await command(env, '/' + arg, '', reply)
      return json({ ok: true })
    }
    await tg(env, 'answerCallbackQuery', { callback_query_id: cb.id, text: answer.slice(0, 190) })
    if (answer && cb.message) {
      await tg(env, 'editMessageReplyMarkup', { chat_id: chat, message_id: cb.message.message_id, reply_markup: { inline_keyboard: [] } })
      await reply(answer)
    }
    return json({ ok: true })
  }

  if (msg && typeof msg.text === 'string') {
    const t = msg.text.trim()
    const space = t.search(/\s/)
    const cmd = (space < 0 ? t : t.slice(0, space)).split('@')[0].toLowerCase()
    const rest = space < 0 ? '' : t.slice(space + 1).trim()
    await command(env, cmd, rest, reply)
  }
  return json({ ok: true })
}

async function command(env, cmd, rest, reply) {
  switch (cmd) {
    case '/start':
      return reply('Панель APEX VPN. Выберите действие:', MENU)
    case '/help':
      return reply(HELP)
    case '/update': {
      const pub = await kvJson(env, 'update', null)
      const builds = await kvJson(env, 'builds', [])
      let out = '<b>Обновления</b>\n\n' +
        (pub ? `Опубликована: <b>${escapeHtml(pub.versionName)}</b>${pub.mandatory ? ' (обязательная)' : ''}\n` : 'Ничего не опубликовано\n')
      if (pub) out += `Скачиваний: ${(await env.APEX.get('downloads:' + pub.versionCode)) || 0}\n`
      out += '\nПоследние сборки:'
      const keyboard = []
      for (const code of builds) {
        const b = await kvJson(env, 'build:' + code, null)
        if (!b) continue
        out += `\n• ${escapeHtml(b.versionName)} (код ${code}, ${formatSize(b.size)}, ${new Date(b.at).toISOString().slice(0, 16).replace('T', ' ')} UTC)`
        keyboard.push([{ text: `🚀 Опубликовать ${b.versionName}`, callback_data: 'pub:' + code }])
      }
      return reply(out, keyboard.length ? { inline_keyboard: keyboard } : undefined)
    }
    case '/unpublish':
      await env.APEX.delete('update')
      return reply('Обновление снято с публикации.')
    case '/news':
    case '/warn': {
      if (!rest) return reply('Напишите текст после команды, например:\n<code>/news Новые серверы\nДобавили Японию и Корею!</code>')
      const [first, ...others] = rest.split('\n')
      const hasTitle = others.length > 0
      await env.APEX.put('news', JSON.stringify({
        id: 'n' + Date.now(),
        title: hasTitle ? first : '',
        text: hasTitle ? others.join('\n') : first,
        level: cmd === '/warn' ? 'warning' : 'info',
      }))
      return reply('📢 Объявление опубликовано. Пользователи увидят его на главном экране.')
    }
    case '/news_off':
      await env.APEX.delete('news')
      return reply('Объявление убрано.')
    case '/maintenance': {
      if (!rest) return reply('Напишите текст, например:\n<code>/maintenance Сервер в Германии обновляется до 15:00</code>')
      await env.APEX.put('maintenance', JSON.stringify({ text: rest, at: Date.now() }))
      return reply('🛠 Баннер «Технические работы» включён.')
    }
    case '/maintenance_off':
      await env.APEX.delete('maintenance')
      return reply('✅ Технические работы завершены, баннер убран.')
    case '/maint':
      return reply('Включить: <code>/maintenance текст</code>\nВыключить: /maintenance_off')
    case '/stats':
      return reply(await stats(env))
    case '/crashes': {
      const list = await kvJson(env, 'crashes', [])
      if (!list.length) return reply('Сбоев не было 🎉')
      return reply('<b>Последние сбои</b>\n\n' + list.slice(0, 5).map((c) =>
        `<b>${new Date(c.at).toISOString().slice(0, 16).replace('T', ' ')} UTC</b>\n<pre>${escapeHtml(c.text.slice(0, 500))}</pre>`).join('\n'))
    }
    default:
      return reply(HELP, MENU)
  }
}

async function stats(env) {
  const now = Date.now()
  const day = 86400000
  let total = 0, d1 = 0, d7 = 0
  const versions = {}
  let cursor
  do {
    const page = await env.APEX.list({ prefix: 'dev:', cursor })
    for (const k of page.keys) {
      const v = await env.APEX.get(k.name, 'json')
      if (!v) continue
      total++
      if (now - v.t < day) d1++
      if (now - v.t < 7 * day) d7++
      versions[v.v] = (versions[v.v] || 0) + 1
    }
    cursor = page.list_complete ? undefined : page.cursor
  } while (cursor)
  const lines = Object.entries(versions).sort((a, b) => b[1] - a[1]).map(([v, n]) => `• ${escapeHtml(v)} — ${n}`)
  return '<b>Статистика</b>\n\n' +
    `За сутки: <b>${d1}</b>\nЗа неделю: <b>${d7}</b>\nЗа месяц: <b>${total}</b>\n\n` +
    '<b>Версии</b>\n' + (lines.join('\n') || 'нет данных')
}
