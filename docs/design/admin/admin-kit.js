(function(){
// Crichere admin kit: tokens, components, charts and frames shared by the P16 boards (docs/PHASE20.md).
const AK = window.AK = {}
const inr = AK.inr = n => Number(n).toLocaleString('en-IN')
const sum = AK.sum = a => a.reduce((s, v) => s + v, 0)
const icon = AK.icon = (n, cls = '') => `<span class="ic ${cls}">${n}</span>`
AK.C = { light: ['#12936A', '#2A78D6', '#C2410C'], dark: ['#199E70', '#3987E5', '#D95926'], ramp: ['#E7F3EE', '#C6E3D8', '#5FB294', '#2E9070', '#0B6E4F', '#084C38'] }
AK.c = theme => theme === 'dark' ? AK.C.dark : AK.C.light

// ---------- components ----------
const ST = { ok: 'check_circle', warn: 'error', danger: 'cancel', info: 'info', neutral: 'radio_button_unchecked', live: 'sensors', internal: 'shield_person' }
AK.st = (k, t) => `<span class="st ${k}">${icon(ST[k])}${t}</span>`
AK.av = (n, cls = '') => `<span class="av ${cls}">${n.split(' ').map(w => w[0]).slice(0, 2).join('')}</span>`
AK.avn = (cls = '') => `<span class="av neutral ${cls}">${icon('person')}</span>`
AK.lsq = (t, bg = '#0B6E4F', cls = '') => `<span class="lsq ${cls}" style="background:${bg}">${t}</span>`
AK.btn = (t, k = '', i = '') => `<span class="btn ${k}">${i ? icon(i) : ''}${t}</span>`
AK.input = (v, o = {}) => `<div class="input${o.ph ? ' ph' : ''}${o.focus ? ' focus' : ''}${o.area ? ' area' : ''}"${o.w ? ` style="width:${o.w}px"` : ''}>${o.ic ? icon(o.ic) : ''}<span>${v}</span>${o.end || ''}</div>`
AK.field = (label, inner, help = '') => `<div class="field"><label>${label}</label>${inner}${help ? `<span class="sub">${help}</span>` : ''}</div>`
AK.tabs = (items, active) => `<div class="tabbar">${items.map(([n, c]) => `<span class="${n === active ? 'on' : ''}">${n}${c != null ? `<b class="cnt">${c}</b>` : ''}</span>`).join('')}</div>`
AK.chip = (t, on = false, i = '') => `<span class="chip${on ? ' on' : ''}">${i ? icon(i) : ''}${t}</span>`
AK.seg = (items, on) => `<div class="segctl">${items.map(t => `<span class="${t === on ? 'on' : ''}">${t}</span>`).join('')}</div>`
AK.filters = items => `<div class="filters">${items.join('')}</div>`
AK.say = t => `<p class="say">${t}</p>`
AK.liveDot = (t, stale) => `<span class="live${stale ? ' stale' : ''}"><i></i>${t}</span>`
AK.panel = (title, sub, body, cls = '', right) => `<section class="panel ${cls}"><header class="ph"><div class="col"><h3>${title}</h3>${sub ? `<span class="sub">${sub}</span>` : ''}</div>${right === undefined ? `<span class="tv">${icon('table_rows')}Table</span>` : right}</header>${body}</section>`
AK.table = (cols, rows) => `<table class="tbl"><thead><tr>${cols.map(c => `<th class="${c.r ? 'r' : ''}"${c.w ? ` style="width:${c.w}px"` : ''}>${c.t}</th>`).join('')}</tr></thead><tbody>${rows.map(r => `<tr class="${r.sel ? 'sel' : ''}">${r.c.map((v, i) => `<td class="${cols[i].r ? 'r' : ''}">${v}</td>`).join('')}</tr>`).join('')}</tbody></table>`
AK.pager = (left, prev = false) => `<div class="pager"><span>${left}</span><div style="display:flex;gap:8px">${AK.btn('Previous', 'sm' + (prev ? '' : ' dis'), 'chevron_left')}${AK.btn('Next', 'sm', 'chevron_right')}</div></div>`
AK.dialog = (title, body, acts, w = 480) => `<div class="scrim"></div><div class="dlg" style="width:${w}px"><h3>${title}</h3>${body}<div class="acts">${acts}</div></div>`
AK.cellPerson = (name, sub, neutral) => `<div class="cell">${neutral ? AK.avn() : AK.av(name)}<div class="col"><span class="strong">${name}</span>${sub ? `<span class="sub">${sub}</span>` : ''}</div></div>`
AK.cellLeague = (name, sub, bg) => `<div class="cell">${AK.lsq(name.split(' ').map(w => w[0]).slice(0, 2).join(''), bg)}<div class="col"><span class="strong">${name}</span>${sub ? `<span class="sub">${sub}</span>` : ''}</div></div>`
AK.kv = pairs => `<div class="kvg">${pairs.map(([k, v]) => `<span>${k}</span><span>${v}</span>`).join('')}</div>`
AK.imgph = (t, h, w = '100%') => `<div class="imgph" style="height:${h}px;width:${typeof w === 'number' ? w + 'px' : w}">${t}</div>`
AK.banner = (k, i, html) => `<div class="banner ${k}">${icon(i, k)}<div>${html}</div></div>`

// ---------- charts ----------
AK.legend = items => `<div class="legend">${items.map(([c, k]) => `<span class="lg"><b style="background:${c}"></b>${k}</span>`).join('')}</div>`
AK.line = ({ series, w, h, max, colors, labels, ticks, theme, hoverAt, x = ['23 Oct', '21 Nov'], area = true }) => {
  const dark = theme === 'dark', grid = dark ? '#232D36' : '#E3E6E2', ink2 = dark ? '#A7B2AC' : '#5C645F'
  const step = w / (series[0].length - 1), y = v => (h - (v / max) * h).toFixed(1)
  let s = `<svg width="${w + 44}" height="${h + 26}" viewBox="-36 -6 ${w + 44} ${h + 26}" style="display:block;overflow:visible">`
  for (const t of ticks) s += `<line x1="0" x2="${w}" y1="${y(t)}" y2="${y(t)}" stroke="${grid}"></line><text x="-8" y="${+y(t) + 4}" text-anchor="end" font-family="IBM Plex Mono" font-size="11" fill="${ink2}">${inr(t)}</text>`
  if (series.length === 1 && area) s += `<path d="M0,${h} L${series[0].map((v, i) => `${(i * step).toFixed(1)},${y(v)}`).join(' L')} L${w},${h} Z" fill="${colors[0]}" fill-opacity="${dark ? 0.22 : 0.12}"></path>`
  series.forEach((ser, k) => {
    s += `<polyline points="${ser.map((v, i) => `${(i * step).toFixed(1)},${y(v)}`).join(' ')}" fill="none" stroke="${colors[k]}" stroke-width="2" stroke-linejoin="round"></polyline>`
    if (labels) s += `<text x="${w + 6}" y="${+y(ser[ser.length - 1]) + 4}" font-family="Inter" font-size="11" font-weight="500" fill="${ink2}">${labels[k]}</text>`
  })
  if (hoverAt != null) {
    const xx = (hoverAt * step).toFixed(1)
    s += `<line x1="${xx}" x2="${xx}" y1="0" y2="${h}" stroke="${dark ? '#74807A' : '#8A928C'}"></line>`
    series.forEach((ser, k) => { s += `<circle cx="${xx}" cy="${y(ser[hoverAt])}" r="4" fill="${colors[k]}" stroke="${dark ? '#10161C' : '#FFFFFF'}" stroke-width="2"></circle>` })
  }
  s += `<text x="0" y="${h + 18}" font-family="IBM Plex Mono" font-size="11" fill="${ink2}">${x[0]}</text><text x="${w}" y="${h + 18}" text-anchor="end" font-family="IBM Plex Mono" font-size="11" fill="${ink2}">${x[1]}</text>`
  return s + '</svg>'
}
AK.columns = (values, { h, color, width, x }) => {
  const max = Math.max(...values, 1)
  return `<div class="cols" style="height:${h}px;width:${width}px">` + values.map(v => `<div class="cc"><span class="num cv">${v}</span><div style="height:${Math.max(v / max * (h - 18), v ? 3 : 0)}px;background:${color}"></div></div>`).join('') + '</div>' + (x ? `<div class="xax sub"><span>${x[0]}</span><span>${x[1]}</span></div>` : '')
}
AK.paired = (a, b, { h, ca, cb, width, x }) => {
  const max = Math.max(...a, ...b, 1)
  return `<div class="cols" style="height:${h}px;width:${width}px;gap:6px">` + a.map((v, i) => `<div class="cp"><div style="height:${v / max * h}px;background:${ca}"></div><div style="height:${b[i] / max * h}px;background:${cb}"></div></div>`).join('') + '</div>' + (x ? `<div class="xax sub" style="width:${width}px"><span>${x[0]}</span><span>${x[1]}</span></div>` : '')
}
AK.ranked = (rows, { color, max }) => {
  const m = max ?? Math.max(...rows.map(r => r[1]))
  return '<div class="rank">' + rows.map(([k, v]) => `<div class="rrow"><span class="rk">${k}</span><div class="rtrack"><div style="width:${(v / m * 100).toFixed(1)}%;background:${color}"></div></div><span class="num rv">${inr(v)}</span></div>`).join('') + '</div>'
}
AK.funnel = rows => {
  const top = rows[0][1], cs = ['#084C38', '#0B6E4F', '#2E9070', '#5FB294']
  return '<div class="rank">' + rows.map(([k, v], i) => `<div class="frow"><div class="fhead"><span class="rk">${k}</span><span class="num rv">${inr(v)}</span></div><div class="rtrack tall"><div style="width:${(v / top * 100).toFixed(1)}%;background:${cs[i]}"></div></div><span class="sub">${i ? `${Math.round(v / rows[i - 1][1] * 100)}% of previous step` : 'Everyone who asked for a code'}</span></div>`).join('') + '</div>'
}
AK.segbar = (rows, colors, inks) => `<div class="segb">${rows.map(([k, v], i) => `<div style="flex:${v};background:${colors[i]};color:${inks[i]}">${v}</div>`).join('')}</div><div class="legend">${rows.map(([k], i) => `<span class="lg"><i style="background:${colors[i]}"></i>${k}</span>`).join('')}</div>`
AK.heat = cohorts => {
  const r = AK.C.ramp, ramp = v => v >= 90 ? [r[5], '#FFF'] : v >= 55 ? [r[4], '#FFF'] : v >= 45 ? [r[3], '#FFF'] : v >= 38 ? [r[2], '#0E1116'] : v >= 34 ? [r[1], '#0E1116'] : [r[0], '#0E1116']
  let s = '<div class="heat"><span></span>' + [0, 1, 2, 3, 4, 5].map(w => `<span class="hh">Week ${w}</span>`).join('')
  for (const [label, vals] of cohorts) {
    s += `<span class="hl">${label}</span>`
    for (let i = 0; i < 6; i++) { const v = vals[i]; if (v == null) s += '<span class="hc empty"></span>'; else { const [bg, fg] = ramp(v); s += `<span class="hc" style="background:${bg};color:${fg}">${v}%</span>` } }
  }
  return s + '</div>'
}
AK.meter = (v, max, label, color = '#0B6E4F') => `<div class="meter"><div style="width:${(v / max * 100).toFixed(1)}%;background:${color}"></div></div><span class="sub">${label}</span>`
AK.spark = (arr, color, w = 96, h = 26) => {
  const max = Math.max(...arr), min = Math.min(...arr), st = w / (arr.length - 1)
  return `<svg width="${w}" height="${h}" style="display:block"><polyline points="${arr.map((v, i) => `${(i * st).toFixed(1)},${(h - 2 - (v - min) / (max - min || 1) * (h - 4)).toFixed(1)}`).join(' ')}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round"></polyline></svg>`
}
AK.cal = (weeks, seed = 7) => {
  let x = seed, out = ''
  const rnd = () => (x = (x * 9301 + 49297) % 233280) / 233280
  for (let i = 0; i < weeks * 7; i++) {
    const v = rnd(), lvl = i > weeks * 7 - 40 ? (v > .35 ? Math.ceil(v * 4) : 0) : (v > .7 ? Math.ceil((v - .7) * 13) : 0)
    out += `<i class="l${Math.min(lvl, 4)}"></i>`
  }
  return `<div class="cal">${out}</div>`
}

// ---------- frames ----------
const NAV = [['Overview', 'monitoring'], ['Reports', 'flag', 3], ['Leagues', 'emoji_events'], ['Users', 'group'], ['Suspended', 'block'], ['Audit log', 'history']]
AK.sidebar = active => `<aside class="side"><div class="brandrow"><span class="logo">C</span><span class="wm">Crichere<small>Admin</small></span></div>${NAV.map(([n, i, c]) => `<span class="nav${n === active ? ' on' : ''}">${icon(i)}${n}${c ? `<span class="cnt">${c}</span>` : ''}</span>`).join('')}<div class="sidefoot"><span class="sub hw">${icon('error', 'warn')}1 health warning</span><div class="who"><span class="av">AV</span><div class="col"><span class="strong">aranyventures</span><span class="sub">Admin</span></div>${icon('unfold_more', 'muted')}</div></div></aside>`
AK.topbar = (live = 'Live, updated 3 s ago', stale) => `<div class="topbar"><div class="search">${icon('search')}Search leagues and people<kbd>/</kbd></div><span style="margin-left:auto">${live ? AK.liveDot(live, stale) : ''}</span></div>`
AK.frame = ({ theme = 'light', active = 'Overview', title, crumb = '', sub = '', head = '', body, label, width = 1440, minH = 0, overlay = '', live, stale }) =>
  `<div class="app${theme === 'dark' ? ' dark' : ''}" style="width:${width}px;${minH ? `min-height:${minH}px;` : ''}display:flex;position:relative" data-screen-label="${label}">${AK.sidebar(active)}<div class="mainc">${AK.topbar(live, stale)}<main class="mainb">${title ? `<div class="pagehead"><div class="col" style="gap:6px">${crumb ? `<span class="crumb">${crumb}</span>` : ''}<h1>${title}</h1>${sub ? `<span class="sub">${sub}</span>` : ''}</div><div class="hacts">${head}</div></div>` : ''}${body}</main></div>${overlay}</div>`
const PT = [['Overview', 'monitoring'], ['Reports', 'flag'], ['Leagues', 'emoji_events'], ['Users', 'group'], ['More', 'more_horiz']]
AK.phone = ({ theme = 'light', title, back = false, right = '', body, active = 'Overview', label, tabs = true, overlay = '' }) =>
  `<div class="app phone${theme === 'dark' ? ' dark' : ''}" style="display:flex;flex-direction:column;position:relative" data-screen-label="${label}"><div class="pbar"><div class="pt">${back ? icon('arrow_back') : '<span class="logo">C</span>'}<span class="wm">${title}</span></div>${right}</div><div class="pbody">${body}</div>${tabs ? `<div class="tabs">${PT.map(([n, i]) => `<span class="${n === active ? 'on' : ''}">${icon(i)}${n}</span>`).join('')}</div>` : ''}${overlay}</div>`
AK.board = (id, cap, inner, note = '', w) => `<div id="${id}" class="board"${w ? ` style="width:${w}px"` : ''}><div class="tag"><span class="id">${id}</span><span class="cap">${cap}</span></div>${inner}${note ? `<p class="note">${note}</p>` : ''}</div>`
AK.section = (id, title, desc, boards) => `<section id="${id}" class="sec"><div class="sechead"><h2>${title}</h2><span>${desc}</span></div><div class="boards">${boards.join('')}</div></section>`

// ---------- styles ----------
AK.css = `
body{margin:0;background:#F1F3F0;-webkit-font-smoothing:antialiased;font-family:Inter,sans-serif;color:#0E1116}
a{color:#0B6E4F;text-decoration:none} a:hover{color:#0A5F45}
.sec{display:flex;flex-direction:column;gap:20px}
.sechead{display:flex;align-items:baseline;gap:12px;border-top:2px solid #0E1116;padding-top:12px}
.sechead h2{margin:0;font:700 22px/1 Archivo,sans-serif;letter-spacing:-.02em}
.sechead>span{font:400 13px/1.4 Inter,sans-serif;color:#5C645F}
.boards{display:flex;gap:40px;align-items:flex-start;flex-wrap:wrap}
.board{display:flex;flex-direction:column;gap:12px}
.tag{display:flex;align-items:center;gap:10px}
.id{font:600 12px/1 'IBM Plex Mono',monospace;padding:5px 8px;background:#0E1116;color:#fff;border-radius:6px}
.cap{font:500 13px/1 Inter,sans-serif;color:#434945}
.note{margin:0;max-width:720px;font:400 12.5px/1.55 Inter,sans-serif;color:#5C645F;text-wrap:pretty}
.ic{font-family:'Material Symbols Rounded';font-size:18px;line-height:1;font-weight:400;font-style:normal;display:inline-block;vertical-align:-4px}
.num{font-family:'IBM Plex Mono',monospace;font-variant-numeric:tabular-nums}
.col{display:flex;flex-direction:column;gap:3px;min-width:0}
.row{display:flex;align-items:center;gap:8px}
.end{align-items:flex-end}
.sub{font:400 12px/1.45 Inter,sans-serif;color:var(--ink3)}
.strong{font-weight:500;color:var(--ink)}
.muted{color:var(--ink3)}
.app{--canvas:#F7F8F7;--s1:#FFFFFF;--s2:#FAFBFA;--s3:#F4F6F5;--hair:#E3E6E2;--hair2:#DDE2DF;--ink:#0E1116;--ink2:#5C645F;--ink3:#8A928C;--brand:#0B6E4F;--wash:#E7F3EE;--brandink:#084C38;--okc:#14764F;--okbg:#E9F7F0;--warnc:#9A5B12;--warnbg:#FDF2E4;--dangerc:#B62E2E;--dangerbg:#FBEAEA;--infoc:#2A5FA8;--infobg:#ECF3FC;
  background:var(--canvas);color:var(--ink);border-radius:14px;overflow:hidden;box-shadow:0 0 0 1px #DDE2DF,0 16px 40px rgba(14,17,22,.10);font-family:Inter,sans-serif}
.app.dark{--canvas:#0A0F14;--s1:#10161C;--s2:#161E25;--s3:#1D262E;--hair:#232D36;--hair2:#33404B;--ink:#F2F5F3;--ink2:#A7B2AC;--ink3:#74807A;--brand:#3FBF8F;--wash:rgba(11,110,79,.24);--brandink:#C6E3D8;--okc:#3FBF8F;--okbg:rgba(31,169,113,.14);--warnc:#F2C27A;--warnbg:rgba(242,166,59,.14);--dangerc:#EF8B8B;--dangerbg:rgba(214,69,69,.16);--infoc:#8DB6EE;--infobg:rgba(59,125,216,.16)}
.ok{color:var(--okc)} .warn{color:var(--warnc)} .danger{color:var(--dangerc)} .info{color:var(--infoc)}
.up{color:var(--okc);font:500 12px/1 Inter,sans-serif;display:flex;align-items:center;gap:2px}
.up .ic{font-size:15px}
.side{width:232px;flex:none;background:var(--s1);border-right:1px solid var(--hair);padding:20px 14px;display:flex;flex-direction:column;gap:4px}
.brandrow{display:flex;align-items:center;gap:10px;padding:0 8px 18px}
.logo{width:30px;height:30px;flex:none;border-radius:8px;background:#0B6E4F;color:#fff;display:flex;align-items:center;justify-content:center;font:800 17px/1 Archivo,sans-serif}
.wm{font:700 17px/1 Archivo,sans-serif;letter-spacing:-.02em}
.wm small{display:block;font:500 11px/1.4 Inter,sans-serif;color:var(--ink3);letter-spacing:0}
.nav{display:flex;align-items:center;gap:10px;height:40px;padding:0 10px;border-radius:8px;font:500 14px/1 Inter,sans-serif;color:var(--ink2)}
.nav.on{background:var(--wash);color:var(--brandink)}
.cnt{margin-left:auto;font:600 11px/1 'IBM Plex Mono',monospace;padding:3px 7px;border-radius:999px;background:var(--warnbg);color:var(--warnc)}
.sidefoot{margin-top:auto;display:flex;flex-direction:column;gap:10px;padding:14px 8px 0;border-top:1px solid var(--hair)}
.hw{display:flex;align-items:center;gap:6px}
.hw .ic{font-size:16px}
.who{display:flex;align-items:center;gap:10px}
.who .ic{margin-left:auto}
.av{width:32px;height:32px;flex:none;border-radius:50%;background:#C6E3D8;color:#084C38;display:flex;align-items:center;justify-content:center;font:600 12px/1 Inter,sans-serif}
.dark .av{background:rgba(11,110,79,.24);color:#F2F5F3}
.av.neutral{background:var(--s3);color:var(--ink3)}
.av.big{width:56px;height:56px;font-size:18px}
.lsq{width:36px;height:36px;flex:none;border-radius:8px;color:#fff;display:flex;align-items:center;justify-content:center;font:700 13px/1 Archivo,sans-serif}
.lsq.big{width:64px;height:64px;border-radius:12px;font-size:22px;box-shadow:0 0 0 3px var(--s1)}
.mainc{flex:1;min-width:0;display:flex;flex-direction:column}
.mainb{padding:28px 32px 40px;display:flex;flex-direction:column;gap:20px}
.topbar{height:60px;display:flex;align-items:center;gap:16px;padding:0 28px;background:var(--s1);border-bottom:1px solid var(--hair)}
.search{flex:1;max-width:420px;height:36px;display:flex;align-items:center;gap:8px;padding:0 12px;border:1px solid var(--hair2);border-radius:8px;color:var(--ink3);font:400 14px/1 Inter,sans-serif;background:var(--s1)}
kbd{font:500 11px/1 'IBM Plex Mono',monospace;padding:3px 6px;border:1px solid var(--hair2);border-radius:4px;color:var(--ink3)}
.search kbd{margin-left:auto}
.live{display:inline-flex;align-items:center;gap:8px;font:500 13px/1 Inter,sans-serif;color:var(--ink2)}
.live i{width:8px;height:8px;border-radius:50%;background:#F2A63B;box-shadow:0 0 0 4px rgba(242,166,59,.2)}
.live.stale i{background:var(--ink3);box-shadow:0 0 0 4px var(--s3)}
.pagehead{display:flex;align-items:flex-end;justify-content:space-between;gap:16px}
.pagehead h1{margin:0;font:700 26px/1.2 Archivo,sans-serif;letter-spacing:-.02em}
.hacts{display:flex;gap:8px;align-items:center}
.crumb{font:500 13px/1 Inter,sans-serif;color:var(--ink3)}
.say{margin:0;font:400 14px/1.55 Inter,sans-serif;color:var(--ink2);max-width:820px;text-wrap:pretty}
.say b{color:var(--ink);font-weight:600}
.filters{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
.segctl{display:flex;border:1px solid var(--hair2);border-radius:8px;overflow:hidden;background:var(--s1)}
.segctl>span{height:34px;display:flex;align-items:center;gap:6px;padding:0 12px;font:500 13px/1 Inter,sans-serif;color:var(--ink2);border-right:1px solid var(--hair);white-space:nowrap}
.segctl>span:last-child{border-right:0}
.segctl .on{background:var(--wash);color:var(--brandink)}
.chip{height:34px;display:inline-flex;align-items:center;gap:6px;padding:0 12px;border:1px solid var(--hair2);border-radius:999px;font:500 13px/1 Inter,sans-serif;color:var(--ink2);background:var(--s1);white-space:nowrap}
.chip.on{background:var(--wash);border-color:transparent;color:var(--brandink)}
.btn{height:36px;display:inline-flex;align-items:center;gap:6px;padding:0 14px;border-radius:8px;border:1px solid var(--hair2);background:var(--s1);color:var(--ink);font:500 14px/1 Inter,sans-serif;white-space:nowrap}
.btn.primary{background:#0B6E4F;border-color:#0B6E4F;color:#fff}
.btn.danger{background:#D64545;border-color:#D64545;color:#fff}
.btn.dangero{color:var(--dangerc)}
.btn.ghost{border-color:transparent;background:transparent;color:var(--ink2)}
.btn.sm{height:30px;padding:0 10px;font-size:13px}
.btn.dis{opacity:.45}
.btn.full{width:100%;justify-content:center;height:44px}
.field{display:flex;flex-direction:column;gap:6px}
.field label{font:500 13px/1 Inter,sans-serif;color:var(--ink2)}
.input{height:38px;display:flex;align-items:center;gap:8px;padding:0 12px;border:1px solid var(--hair2);border-radius:8px;background:var(--s1);font:400 14px/1 Inter,sans-serif;color:var(--ink);box-sizing:border-box}
.input.ph{color:var(--ink3)}
.input.focus{border-color:var(--brand);box-shadow:0 0 0 3px var(--wash)}
.input.area{height:auto;min-height:84px;align-items:flex-start;padding:10px 12px;line-height:1.5}
.input .ic{color:var(--ink3)}
.tablewrap{background:var(--s1);border:1px solid var(--hair);border-radius:12px;overflow:hidden}
.tbl{width:100%;border-collapse:collapse;font:400 13px/1.35 Inter,sans-serif}
.tbl th{text-align:left;font:500 12px/1 Inter,sans-serif;color:var(--ink3);padding:11px 14px;border-bottom:1px solid var(--hair);background:var(--s2);white-space:nowrap}
.tbl td{padding:11px 14px;border-bottom:1px solid var(--hair);vertical-align:middle;color:var(--ink)}
.tbl tr.sel td{background:var(--s3)}
.tbl .r{text-align:right}
.tbl td .ic.go{color:var(--ink3)}
.cell{display:flex;align-items:center;gap:10px;min-width:0}
.pager{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;font:400 13px/1 Inter,sans-serif;color:var(--ink2);background:var(--s1)}
.st{display:inline-flex;align-items:center;gap:4px;height:24px;padding:0 9px;border-radius:999px;font:500 12px/1 Inter,sans-serif;white-space:nowrap}
.st .ic{font-size:15px}
.st.ok{background:var(--okbg);color:var(--okc)} .st.warn{background:var(--warnbg);color:var(--warnc)} .st.danger{background:var(--dangerbg);color:var(--dangerc)} .st.info{background:var(--infobg);color:var(--infoc)} .st.neutral{background:var(--s3);color:var(--ink2)} .st.internal{background:var(--s3);color:var(--ink2)}
.st.live{background:rgba(242,166,59,.16);color:#9A5B12}
.dark .st.live{color:#F2C27A}
.tabbar{display:flex;gap:4px;border-bottom:1px solid var(--hair)}
.tabbar>span{height:42px;display:flex;align-items:center;gap:6px;padding:0 12px;font:500 14px/1 Inter,sans-serif;color:var(--ink2);border-bottom:2px solid transparent;margin-bottom:-1px}
.tabbar .on{color:var(--ink);border-bottom-color:var(--brand)}
.tabbar .cnt{margin:0;background:var(--s3);color:var(--ink2)}
.grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:20px}
.panel{background:var(--s1);border:1px solid var(--hair);border-radius:12px;padding:20px 22px;display:flex;flex-direction:column;gap:16px;min-width:0}
.ph{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}
.ph h3{margin:0;font:600 16px/1.25 Archivo,sans-serif;letter-spacing:-.01em}
.tv{display:inline-flex;align-items:center;gap:4px;font:500 12px/1 Inter,sans-serif;color:var(--ink3);white-space:nowrap}
.tv .ic{font-size:16px}
.c3{grid-column:span 3}.c4{grid-column:span 4}.c5{grid-column:span 5}.c6{grid-column:span 6}.c7{grid-column:span 7}.c8{grid-column:span 8}.c12{grid-column:span 12}
.sechd{grid-column:span 12;display:flex;flex-direction:column;gap:6px;margin-top:14px}
.sechd .row{gap:12px;align-items:baseline}
.sechd h2{margin:0;font:700 19px/1.2 Archivo,sans-serif;letter-spacing:-.015em}
.kpis{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));background:var(--s1);border:1px solid var(--hair);border-radius:12px}
.kpi{padding:18px 20px;display:flex;flex-direction:column;gap:8px;border-right:1px solid var(--hair);color:inherit}
.kpi:last-child{border-right:0}
.kk{font:500 13px/1 Inter,sans-serif;color:var(--ink2)}
.kv{font-size:30px;font-weight:600;line-height:1;letter-spacing:-.02em}
.kpi.alert{background:var(--warnbg);border-radius:0 11px 11px 0}
.go{display:flex;align-items:center;font:500 13px/1 Inter,sans-serif;color:var(--brand)}
.legend{display:flex;flex-wrap:wrap;gap:14px;align-items:center}
.lg{display:inline-flex;align-items:center;gap:6px;font:400 12px/1 Inter,sans-serif;color:var(--ink2)}
.lg b{width:14px;height:2px;border-radius:2px;display:inline-block}
.lg i{width:10px;height:10px;border-radius:3px;display:inline-block}
.tip{position:absolute;padding:10px 12px;background:var(--s1);border:1px solid var(--hair2);border-radius:8px;box-shadow:0 8px 24px rgba(14,17,22,.10);display:flex;flex-direction:column;gap:6px;min-width:150px}
.tip .tr{display:flex;align-items:center;gap:8px;font:400 12px/1 Inter,sans-serif;color:var(--ink2)}
.tip .tr b{width:12px;height:2px;border-radius:2px}
.tip .tr strong{margin-left:auto;font:600 13px/1 'IBM Plex Mono',monospace;color:var(--ink)}
.cols{display:flex;align-items:flex-end;gap:2px;border-bottom:1px solid var(--hair)}
.cc{flex:1;display:flex;flex-direction:column;justify-content:flex-end;align-items:center;gap:4px;height:100%}
.cc>div{width:100%;border-radius:4px 4px 0 0}
.cv{font-size:11px;color:var(--ink2)}
.cp{flex:1;display:flex;align-items:flex-end;gap:2px;height:100%}
.cp>div{flex:1;border-radius:3px 3px 0 0}
.xax{display:flex;justify-content:space-between}
.rank{display:flex;flex-direction:column;gap:10px}
.rrow{display:grid;grid-template-columns:120px minmax(0,1fr) 56px;align-items:center;gap:12px}
.rk{font:400 13px/1.2 Inter,sans-serif;color:var(--ink2)}
.rv{font-size:13px;text-align:right;color:var(--ink)}
.rtrack{height:14px}
.rtrack>div{height:100%;border-radius:0 4px 4px 0}
.rtrack.tall{height:22px;background:var(--s3);border-radius:0 4px 4px 0}
.frow{display:flex;flex-direction:column;gap:5px}
.fhead{display:flex;justify-content:space-between}
.segb{display:flex;gap:2px;height:44px;border-radius:6px;overflow:hidden}
.segb>div{display:flex;align-items:center;justify-content:center;font:600 12px/1 'IBM Plex Mono',monospace}
.heat{display:grid;grid-template-columns:56px repeat(6,minmax(0,1fr));gap:2px;align-items:center}
.hh{font:500 11px/1 Inter,sans-serif;color:var(--ink3);text-align:center;padding-bottom:4px}
.hl{font:400 12px/1 'IBM Plex Mono',monospace;color:var(--ink2)}
.hc{height:28px;border-radius:4px;display:flex;align-items:center;justify-content:center;font:500 11px/1 'IBM Plex Mono',monospace}
.hc.empty{border:1px dashed var(--hair)}
.meter{height:10px;border-radius:999px;background:var(--s3);overflow:hidden}
.meter div{height:100%;border-radius:999px}
.big{font:600 28px/1 'IBM Plex Mono',monospace;letter-spacing:-.02em}
.lcard{border:1px solid var(--hair);border-radius:10px;padding:14px 16px;display:flex;flex-direction:column;gap:10px;background:var(--s2)}
.lhead{display:flex;justify-content:space-between;align-items:center}
.lname{font:600 15px/1.25 Archivo,sans-serif}
.lrow{display:flex;justify-content:space-between;align-items:flex-end;gap:12px}
.bid{font-size:20px;font-weight:600;color:#9A5B12}
.dark .bid{color:#F2A63B}
.lfoot{display:flex;flex-wrap:wrap;gap:6px 16px;font:400 12px/1 Inter,sans-serif;color:var(--ink2);padding-top:10px;border-top:1px solid var(--hair)}
.lfoot .ic{font-size:15px;margin-right:4px}
.lfoot b{color:var(--ink);font-weight:600}
.feed{display:flex;flex-direction:column}
.fi{display:grid;grid-template-columns:76px minmax(0,1fr);gap:10px;padding:10px 0;border-top:1px solid var(--hair)}
.fi:first-child{border-top:0}
.ft{font-size:11px;color:var(--ink3);padding-top:2px}
.fa{font:500 13px/1.3 Inter,sans-serif}
.hrow{display:grid;grid-template-columns:24px 160px minmax(0,1fr);gap:8px;align-items:center;padding:9px 0;border-top:1px solid var(--hair);font:400 13px/1.3 Inter,sans-serif}
.hrow:first-child{border-top:0}
.scrim{position:absolute;inset:0;background:rgba(14,17,22,.45)}
.dark .scrim{background:rgba(0,0,0,.6)}
.dlg{position:absolute;left:50%;top:180px;transform:translateX(-50%);background:var(--s1);border-radius:14px;box-shadow:0 16px 40px rgba(14,17,22,.16);padding:24px;display:flex;flex-direction:column;gap:16px;box-sizing:border-box}
.dark .dlg{background:var(--s2);border:1px solid var(--hair);box-shadow:none}
.dlg h3{margin:0;font:700 20px/1.25 Archivo,sans-serif;letter-spacing:-.015em}
.dlg p{margin:0;font:400 14px/1.55 Inter,sans-serif;color:var(--ink2)}
.dlg .acts{display:flex;justify-content:flex-end;gap:8px;padding-top:4px}
.list{margin:0;padding:0;list-style:none;display:flex;flex-direction:column;gap:8px}
.list li{display:grid;grid-template-columns:20px minmax(0,1fr);gap:8px;font:400 14px/1.5 Inter,sans-serif;color:var(--ink)}
.list li .ic{font-size:17px;color:var(--ink3);vertical-align:-3px}
.kvg{display:grid;grid-template-columns:170px minmax(0,1fr);font:400 14px/1.45 Inter,sans-serif}
.kvg>span{padding:10px 0;border-top:1px solid var(--hair)}
.kvg>span:nth-child(odd){color:var(--ink3);font-size:13px}
.kvg>span:nth-child(1),.kvg>span:nth-child(2){border-top:0}
.imgph{background:repeating-linear-gradient(135deg,var(--s3) 0 8px,var(--s2) 8px 16px);display:flex;align-items:center;justify-content:center;font:500 11px/1.5 'IBM Plex Mono',monospace;color:var(--ink3);text-align:center;border-radius:8px;box-sizing:border-box;padding:8px}
.banner{display:flex;gap:10px;align-items:flex-start;padding:12px 14px;border-radius:10px;font:400 13px/1.5 Inter,sans-serif;color:var(--ink)}
.banner.warn{background:var(--warnbg)} .banner.info{background:var(--infobg)} .banner.danger{background:var(--dangerbg)} .banner.neutral{background:var(--s3)}
.banner b{font-weight:600}
.menu{position:absolute;width:260px;background:var(--s1);border:1px solid var(--hair);border-radius:10px;box-shadow:0 8px 24px rgba(14,17,22,.12);padding:6px;display:flex;flex-direction:column;box-sizing:border-box}
.menu .mi{height:40px;display:flex;align-items:center;gap:10px;padding:0 10px;border-radius:6px;font:400 14px/1 Inter,sans-serif;color:var(--ink)}
.menu .mi .ic{color:var(--ink3)}
.menu hr{border:0;border-top:1px solid var(--hair);margin:6px 0}
.toast{position:absolute;left:50%;bottom:24px;transform:translateX(-50%);background:#0E1116;color:#F2F5F3;border-radius:8px;padding:12px 16px;display:flex;gap:16px;align-items:center;font:400 14px/1 Inter,sans-serif;white-space:nowrap}
.toast b{color:#5FB294;font-weight:500}
.dark .toast{background:#F2F5F3;color:#0E1116}
.empty{display:flex;flex-direction:column;align-items:center;gap:10px;padding:48px 24px;text-align:center}
.empty>.ic{font-size:32px;color:var(--ink3)}
.empty h3{margin:0;font:600 17px/1.3 Archivo,sans-serif}
.empty p{margin:0;font:400 14px/1.5 Inter,sans-serif;color:var(--ink2);max-width:420px}
.fade{opacity:.45}
.progress{height:3px;background:var(--wash);position:relative;overflow:hidden}
.progress i{position:absolute;left:20%;width:35%;top:0;bottom:0;background:var(--brand)}
.tl{display:flex;flex-direction:column}
.tli{display:grid;grid-template-columns:120px 14px minmax(0,1fr);gap:10px;padding:9px 0;font:400 13px/1.45 Inter,sans-serif}
.tli .dot{width:10px;height:10px;border-radius:50%;background:var(--hair2);margin-top:4px}
.tli .dot.b{background:var(--brand)} .tli .dot.w{background:#F2A63B}
.cal{display:grid;grid-template-rows:repeat(7,13px);grid-auto-flow:column;grid-auto-columns:13px;gap:3px}
.cal i{border-radius:3px;background:var(--s3)}
.cal .l1{background:#C6E3D8}.cal .l2{background:#5FB294}.cal .l3{background:#2E9070}.cal .l4{background:#0B6E4F}
.toggle{width:36px;height:20px;border-radius:999px;background:var(--hair2);position:relative;flex:none}
.toggle i{position:absolute;top:2px;left:2px;width:16px;height:16px;border-radius:50%;background:#fff}
.toggle.on{background:#0B6E4F}.toggle.on i{left:18px}
.check{width:18px;height:18px;border-radius:4px;border:1.5px solid var(--hair2);flex:none;display:flex;align-items:center;justify-content:center;box-sizing:border-box}
.check.on{background:#0B6E4F;border-color:#0B6E4F;color:#fff}
.check.on .ic{font-size:14px}
.phone{width:390px;border-radius:28px}
.pbar{height:56px;display:flex;align-items:center;justify-content:space-between;padding:0 16px;background:var(--s1);border-bottom:1px solid var(--hair)}
.pt{display:flex;align-items:center;gap:10px}
.pbody{padding:16px;display:flex;flex-direction:column;gap:14px;flex:1}
.pk{display:grid;grid-template-columns:1fr 1fr;background:var(--s1);border:1px solid var(--hair);border-radius:12px}
.pk .kpi{border-right:1px solid var(--hair);border-bottom:1px solid var(--hair);padding:14px}
.pk .kpi:nth-child(2n){border-right:0}
.pk .kv{font-size:24px}
.tabs{height:60px;display:flex;justify-content:space-around;align-items:center;background:var(--s1);border-top:1px solid var(--hair)}
.tabs>span{display:flex;flex-direction:column;align-items:center;gap:4px;font:500 11px/1 Inter,sans-serif;color:var(--ink3)}
.tabs .on{color:var(--brand)}
.pcard{background:var(--s1);border:1px solid var(--hair);border-radius:12px;padding:14px;display:flex;flex-direction:column;gap:8px}
`
})()
