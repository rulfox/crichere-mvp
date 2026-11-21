(function(){
// Builds P15 Admin Overview (turn 1): three directions for the admin Overview. Source: docs/PHASE20.md.

const inr = n => n.toLocaleString('en-IN')
const sum = a => a.reduce((s, v) => s + v, 0)

// ---------- sample data (fictional, small and honest) ----------
const newUsers = [2, 4, 3, 5, 2, 6, 4, 3, 7, 5, 4, 2, 3, 5, 8, 6, 4, 3, 2, 5, 4, 6, 3, 2, 4, 5, 3, 2, 1, 3]
const prevNew = 55
const dau = [96, 104, 99, 112, 131, 140, 118, 101, 108, 115, 109, 126, 149, 152, 120, 110, 117, 121, 113, 138, 158, 161, 127, 119, 124, 130, 122, 141, 166, 142]
const wau = dau.map((v, i) => Math.round(360 + i * 2.4 + (v - 120) * 0.6))
const mau = dau.map((v, i) => Math.round(560 + i * 1.7))
const funnel = [['Code sent', 1920], ['Signed in', 1544], ['Profile done', 1302], ['Joined a league', 688]]
const status = [['Draft', 9], ['Registration open', 14], ['Auction live', 2], ['Auction done', 8], ['Completed', 4]]
const statusColors = ['#C6E3D8', '#5FB294', '#F2A63B', '#2E9070', '#084C38']
const auctionsWeek = [1, 0, 2, 1, 3, 2, 1, 2]
const districts = [['Alappuzha', 312], ['Ernakulam', 268], ['Kottayam', 190], ['Thrissur', 141], ['Kozhikode', 97], ['Other districts', 276]]
const opened = [1, 0, 2, 1, 0, 3, 1, 0, 1, 2, 0, 1, 2, 1]
const closed = [0, 1, 1, 2, 0, 1, 2, 1, 0, 1, 1, 1, 1, 2]
const reasons = [['Inappropriate image', 1], ['Spam or misleading', 1], ['Something else', 1]]
const cohorts = [
  ['12 Oct', [100, 58, 44, 39, 35, 33]], ['19 Oct', [100, 61, 47, 40, 37]], ['26 Oct', [100, 55, 42, 38]],
  ['2 Nov', [100, 63, 49]], ['9 Nov', [100, 60]], ['16 Nov', [100]],
]
const live = [
  { league: 'Vembanad Premier League 2026', district: 'Alappuzha', lot: 14, of: 40, player: 'Arjun Pillai', bid: 45000, lead: 'Backwater Blasters', bpm: 6, watching: 38 },
  { league: 'Kuttanad Cup', district: 'Alappuzha', lot: 3, of: 24, player: 'Rahul Joseph', bid: 12500, lead: 'Punnamada Pirates', bpm: 2, watching: 11 },
]
const feed = [
  ['2 min ago', 'Player sold for 38,000 pts', 'Vembanad Premier League 2026'],
  ['6 min ago', 'New league created', 'Kottayam'],
  ['11 min ago', 'Auction started', 'Kuttanad Cup'],
  ['18 min ago', '4 players joined', 'Thrissur Weekend Bash'],
  ['26 min ago', 'Report received', 'Inappropriate image'],
  ['41 min ago', 'League completed', 'Ernakulam Corporate T10'],
]
const reports = [
  ['Inappropriate image', 'Profile photo', '19 h ago'],
  ['Spam or misleading', 'League: Kerala Super Bash', '7 h ago'],
  ['Something else', 'Profile: Nikhil Das', '42 min ago'],
]
const health = [
  ['ok', 'Nightly summary', 'Ran 00:10, took 41 s'],
  ['ok', 'Sign-in codes', '3.1% failed today'],
  ['ok', 'SMS today', '214 of 2,000'],
  ['warn', 'MSG91 balance', '8,420 credits, about 18 days'],
  ['ok', 'Alert emails', 'No bounces in 30 days'],
]

// ---------- chart builders (plain SVG/divs, computed here) ----------
function lineChart({ series, w, h, max, colors, labels, ticks, dark, hoverAt }) {
  const grid = dark ? '#232D36' : '#E3E6E2'
  const ink2 = dark ? '#A7B2AC' : '#5C645F'
  const step = w / (series[0].length - 1)
  const y = v => (h - (v / max) * h).toFixed(1)
  let s = `<svg width="${w + 44}" height="${h + 26}" viewBox="-36 -6 ${w + 44} ${h + 26}" style="display:block;overflow:visible">`
  for (const t of ticks) s += `<line x1="0" x2="${w}" y1="${y(t)}" y2="${y(t)}" stroke="${grid}" stroke-width="1"></line><text x="-8" y="${+y(t) + 4}" text-anchor="end" font-family="IBM Plex Mono" font-size="11" fill="${ink2}">${inr(t)}</text>`
  if (series.length === 1) {
    const pts = series[0].map((v, i) => `${(i * step).toFixed(1)},${y(v)}`)
    s += `<path d="M0,${h} L${pts.join(' L')} L${w},${h} Z" fill="${colors[0]}" fill-opacity="${dark ? 0.22 : 0.12}"></path>`
  }
  series.forEach((ser, k) => {
    s += `<polyline points="${ser.map((v, i) => `${(i * step).toFixed(1)},${y(v)}`).join(' ')}" fill="none" stroke="${colors[k]}" stroke-width="2" stroke-linejoin="round"></polyline>`
    if (labels) s += `<text x="${w + 6}" y="${+y(ser[ser.length - 1]) + 4}" font-family="Inter" font-size="11" font-weight="500" fill="${ink2}">${labels[k]}</text>`
  })
  if (hoverAt != null) {
    const x = (hoverAt * step).toFixed(1)
    s += `<line x1="${x}" x2="${x}" y1="0" y2="${h}" stroke="${dark ? '#74807A' : '#8A928C'}" stroke-width="1"></line>`
    series.forEach((ser, k) => { s += `<circle cx="${x}" cy="${y(ser[hoverAt])}" r="4" fill="${colors[k]}" stroke="${dark ? '#10161C' : '#FFFFFF'}" stroke-width="2"></circle>` })
  }
  s += `<text x="0" y="${h + 18}" font-family="IBM Plex Mono" font-size="11" fill="${ink2}">23 Oct</text><text x="${w}" y="${h + 18}" text-anchor="end" font-family="IBM Plex Mono" font-size="11" fill="${ink2}">21 Nov</text>`
  return s + '</svg>'
}

function columns(values, { h, color, gap = 2, width }) {
  const max = Math.max(...values, 1)
  return `<div style="display:flex;align-items:flex-end;gap:${gap}px;height:${h}px;width:${width}px;border-bottom:1px solid var(--hair)">` +
    values.map(v => `<div style="flex:1;display:flex;flex-direction:column;justify-content:flex-end;align-items:center;gap:4px;height:100%"><span class="num" style="font-size:11px;color:var(--ink2)">${v}</span><div style="width:100%;height:${Math.max(v / max * (h - 18), v ? 3 : 0)}px;background:${color};border-radius:4px 4px 0 0"></div></div>`).join('') + '</div>'
}

function pairedColumns(a, b, { h, ca, cb, width }) {
  const max = Math.max(...a, ...b, 1)
  return `<div style="display:flex;align-items:flex-end;gap:6px;height:${h}px;width:${width}px;border-bottom:1px solid var(--hair)">` +
    a.map((v, i) => `<div style="flex:1;display:flex;align-items:flex-end;gap:2px;height:100%"><div style="flex:1;height:${v / max * h}px;background:${ca};border-radius:3px 3px 0 0"></div><div style="flex:1;height:${b[i] / max * h}px;background:${cb};border-radius:3px 3px 0 0"></div></div>`).join('') + '</div>'
}

function rankedBars(rows, { color, max }) {
  const m = max ?? Math.max(...rows.map(r => r[1]))
  return '<div class="rank">' + rows.map(([k, v]) => `<div class="rrow"><span class="rk">${k}</span><div class="rtrack"><div style="width:${(v / m * 100).toFixed(1)}%;height:100%;background:${color};border-radius:0 4px 4px 0"></div></div><span class="num rv">${inr(v)}</span></div>`).join('') + '</div>'
}

function funnelBars(dark) {
  const top = funnel[0][1]
  return '<div class="rank">' + funnel.map(([k, v], i) => {
    const conv = i ? `${Math.round(v / funnel[i - 1][1] * 100)}% of previous` : 'Last 30 days'
    return `<div class="frow"><div class="fhead"><span class="rk">${k}</span><span class="num rv">${inr(v)}</span></div><div class="rtrack tall"><div style="width:${(v / top * 100).toFixed(1)}%;height:100%;background:${['#084C38', '#0B6E4F', '#2E9070', '#5FB294'][i]};border-radius:0 4px 4px 0"></div></div><span class="sub">${conv}</span></div>`
  }).join('') + '</div>'
}

function segmented() {
  const total = sum(status.map(s => s[1]))
  return `<div class="seg">${status.map(([k, v], i) => `<div style="flex:${v};background:${statusColors[i]};display:flex;align-items:center;justify-content:center;color:${i >= 3 ? '#FFFFFF' : '#0E1116'};font:600 12px/1 'IBM Plex Mono',monospace">${v}</div>`).join('')}</div>` +
    `<div class="legend">${status.map(([k], i) => `<span class="lg"><i style="background:${statusColors[i]}"></i>${k}</span>`).join('')}<span class="lg muted">${total} leagues</span></div>`
}

function heatmap() {
  const ramp = v => v >= 90 ? ['#084C38', '#FFF'] : v >= 55 ? ['#0B6E4F', '#FFF'] : v >= 45 ? ['#2E9070', '#FFF'] : v >= 38 ? ['#5FB294', '#0E1116'] : v >= 34 ? ['#C6E3D8', '#0E1116'] : ['#E7F3EE', '#0E1116']
  let s = '<div class="heat"><span></span>' + [0, 1, 2, 3, 4, 5].map(w => `<span class="hh">Week ${w}</span>`).join('')
  for (const [label, vals] of cohorts) {
    s += `<span class="hl">${label}</span>`
    for (let i = 0; i < 6; i++) {
      const v = vals[i]
      if (v == null) s += '<span class="hc empty"></span>'
      else { const [bg, fg] = ramp(v); s += `<span class="hc" style="background:${bg};color:${fg}">${v}%</span>` }
    }
  }
  return s + '</div>'
}

const meter = (v, max, label, color = '#0B6E4F') => `<div class="meter"><div style="width:${(v / max * 100).toFixed(1)}%;background:${color}"></div></div><span class="sub">${label}</span>`

// ---------- shared pieces ----------
const totalNew = sum(newUsers)
const kpis = [
  { k: 'Users', v: '1,284', d: `+${totalNew} new`, dd: `${totalNew - prevNew > 0 ? '+' : ''}${totalNew - prevNew} vs previous 30 days`, spark: newUsers },
  { k: 'Active today', v: inr(dau[dau.length - 1]), d: '23% of monthly', dd: 'Monthly active 610', spark: dau },
  { k: 'Active leagues', v: '37', d: '+5', dd: '2 auctions live now', spark: [28, 29, 31, 30, 32, 33, 35, 37] },
  { k: 'Auctions completed', v: '12', d: '+4', dd: 'Last 30 days', spark: auctionsWeek },
]
const spark = (arr, color, w = 96, h = 26) => {
  const max = Math.max(...arr), min = Math.min(...arr), st = w / (arr.length - 1)
  return `<svg width="${w}" height="${h}" style="display:block"><polyline points="${arr.map((v, i) => `${(i * st).toFixed(1)},${(h - 2 - (v - min) / (max - min || 1) * (h - 4)).toFixed(1)}`).join(' ')}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round"></polyline></svg>`
}
const icon = n => `<span class="ic">${n}</span>`
const statusIcon = s => s === 'ok' ? `<span class="ic ok">check_circle</span>` : `<span class="ic warn">error</span>`

const filterRow = (dark) => `<div class="filters">
  <div class="segctl"><span>Today</span><span>7 days</span><span class="on">30 days</span><span>90 days</span><span>${icon('date_range')}Custom</span></div>
  <span class="chip">${icon('compare_arrows')}Compare to previous 30 days</span>
  <span class="chip">State: Kerala${icon('expand_more')}</span>
  <span class="chip">District: All${icon('expand_more')}</span>
  <span class="chip on">${icon('check')}Internal accounts hidden</span>
</div>`

const liveDot = (txt = 'Live, updated 3 s ago') => `<span class="live"><i></i>${txt}</span>`

function kpiRow(dark) {
  const c = dark ? '#199E70' : '#12936A'
  return `<div class="kpis">${kpis.map(k => `<div class="kpi"><span class="kk">${k.k}</span><span class="kv num">${k.v}</span><div class="kd"><span class="up">${icon('arrow_upward')}${k.d}</span></div>${spark(k.spark, c)}<span class="sub">${k.dd}</span></div>`).join('')}
  <a class="kpi alert"><span class="kk">Open reports</span><span class="kv num">3</span><div class="kd"><span class="warnink">${icon('schedule')}Oldest 19 h</span></div><span class="sub">Target: handled within 24 h</span><span class="go">Open reports queue${icon('chevron_right')}</span></a></div>`
}

const liveCards = (dark) => live.map(a => `<div class="lcard"><div class="lhead"><span class="badge">${icon('sensors')}Live</span><span class="sub">${a.district}</span></div><span class="lname">${a.league}</span><div class="lrow"><div class="col"><span class="sub">Lot ${a.lot} of ${a.of}</span><span class="lplayer">${a.player}</span></div><div class="col end"><span class="sub">Current bid</span><span class="bid num">${inr(a.bid)} pts</span></div></div><div class="lfoot"><span>${icon('gavel')}<b class="num">${a.bpm}</b> bids a minute</span><span>${icon('visibility')}<b class="num">${a.watching}</b> watching</span><span class="lead">Leading: ${a.lead}</span></div></div>`).join('')

const feedList = (n = 6) => `<div class="feed">${feed.slice(0, n).map(([t, a, b]) => `<div class="fi"><span class="ft num">${t}</span><div class="col"><span class="fa">${a}</span><span class="sub">${b}</span></div></div>`).join('')}</div>`

const reportsList = () => `<div class="feed">${reports.map(([r, s, t]) => `<div class="fi"><span class="ic warn">flag</span><div class="col"><span class="fa">${r}</span><span class="sub">${s} · ${t}</span></div></div>`).join('')}</div>`

const healthList = () => `<div class="health">${health.map(([s, k, v]) => `<div class="hrow">${statusIcon(s)}<span class="hk">${k}</span><span class="hv">${v}</span></div>`).join('')}</div>`

function panel(title, sub, body, extra = '') {
  return `<section class="panel ${extra}"><header class="ph"><div class="col"><h3>${title}</h3>${sub ? `<span class="sub">${sub}</span>` : ''}</div><span class="tv">${icon('table_rows')}Table</span></header>${body}</section>`
}

function legend(items) { return `<div class="legend">${items.map(([c, k]) => `<span class="lg"><b style="background:${c}"></b>${k}</span>`).join('')}</div>` }

// ---------- styles ----------
const css = `
body{margin:0;background:#E9ECE9;-webkit-font-smoothing:antialiased;font-family:Inter,sans-serif;color:#0E1116}
.dv-turn{padding:48px 56px 40px;display:flex;flex-direction:column;gap:24px}
.dv-thd{display:flex;align-items:baseline;gap:12px}
.dv-tid{font:600 11px/1 'IBM Plex Mono',monospace;padding:4px 8px;background:#0E1116;color:#fff;border-radius:5px;text-decoration:none}
.dv-tname{font:700 22px/1.2 Archivo,sans-serif;letter-spacing:-.02em}
.intro{margin:0;max-width:880px;font:400 15px/1.6 Inter,sans-serif;color:#434945;text-wrap:pretty}
.dv-opts{display:flex;gap:48px;align-items:flex-start;flex-wrap:nowrap}
.dv-opt{display:flex;flex-direction:column;gap:12px;flex:none}
.dv-olabel{display:flex;align-items:baseline;gap:10px;font:500 13px/1.3 Inter,sans-serif;color:#434945}
.dv-oid{font:600 11px/1 'IBM Plex Mono',monospace;padding:4px 7px;background:rgba(14,17,22,.1);color:#0E1116;border-radius:5px;text-decoration:none}
.dv-next{margin:0;font:400 13px/1.5 Inter,sans-serif;color:#5C645F}
.note{margin:0;max-width:720px;font:400 13px/1.55 Inter,sans-serif;color:#5C645F;text-wrap:pretty}
a{color:#0B6E4F;text-decoration:none} a:hover{color:#0A5F45}
.ic{font-family:'Material Symbols Rounded';font-size:18px;line-height:1;font-weight:400;font-style:normal;display:inline-block;vertical-align:-4px}
.num{font-family:'IBM Plex Mono',monospace;font-variant-numeric:tabular-nums}
.col{display:flex;flex-direction:column;gap:3px;min-width:0}
.end{align-items:flex-end}
.sub{font:400 12px/1.45 Inter,sans-serif;color:var(--ink3)}
.muted{color:var(--ink3)}
/* app frame */
.app{--canvas:#F7F8F7;--s1:#FFFFFF;--s2:#FAFBFA;--s3:#F4F6F5;--hair:#E3E6E2;--hair2:#DDE2DF;--ink:#0E1116;--ink2:#5C645F;--ink3:#8A928C;--brand:#0B6E4F;--wash:#E7F3EE;--brandink:#084C38;--okc:#14764F;--warnc:#9A5B12;--warnbg:#FDF2E4;
  background:var(--canvas);color:var(--ink);border-radius:14px;overflow:hidden;box-shadow:0 0 0 1px #DDE2DF,0 16px 40px rgba(14,17,22,.10)}
.app.dark{--canvas:#0A0F14;--s1:#10161C;--s2:#161E25;--s3:#1D262E;--hair:#232D36;--hair2:#33404B;--ink:#F2F5F3;--ink2:#A7B2AC;--ink3:#74807A;--brand:#3FBF8F;--wash:rgba(11,110,79,.24);--brandink:#C6E3D8;--okc:#3FBF8F;--warnc:#F2C27A;--warnbg:rgba(242,166,59,.14)}
.ok{color:var(--okc)} .warn{color:var(--warnc)} .warnink{color:var(--warnc)}
.up{color:var(--okc);font:500 12px/1 Inter,sans-serif;display:flex;align-items:center;gap:2px}
.up .ic{font-size:15px}
/* nav */
.side{width:232px;flex:none;background:var(--s1);border-right:1px solid var(--hair);padding:20px 14px;display:flex;flex-direction:column;gap:4px}
.brandrow{display:flex;align-items:center;gap:10px;padding:0 8px 18px}
.logo{width:30px;height:30px;border-radius:8px;background:#0B6E4F;color:#fff;display:flex;align-items:center;justify-content:center;font:800 17px/1 Archivo,sans-serif}
.wm{font:700 17px/1 Archivo,sans-serif;letter-spacing:-.02em}
.wm small{display:block;font:500 11px/1.4 Inter,sans-serif;color:var(--ink3);letter-spacing:0}
.nav{display:flex;align-items:center;gap:10px;height:40px;padding:0 10px;border-radius:8px;font:500 14px/1 Inter,sans-serif;color:var(--ink2)}
.nav.on{background:var(--wash);color:var(--brandink)}
.nav .cnt{margin-left:auto;font:600 11px/1 'IBM Plex Mono',monospace;padding:3px 7px;border-radius:999px;background:var(--warnbg);color:var(--warnc)}
.sidefoot{margin-top:auto;display:flex;flex-direction:column;gap:10px;padding:14px 8px 0;border-top:1px solid var(--hair)}
.who{display:flex;align-items:center;gap:10px}
.av{width:30px;height:30px;border-radius:50%;background:#C6E3D8;color:#084C38;display:flex;align-items:center;justify-content:center;font:600 12px/1 Inter,sans-serif}
.topbar{height:60px;display:flex;align-items:center;gap:16px;padding:0 28px;background:var(--s1);border-bottom:1px solid var(--hair)}
.search{flex:1;max-width:420px;height:36px;display:flex;align-items:center;gap:8px;padding:0 12px;border:1px solid var(--hair2);border-radius:8px;color:var(--ink3);font:400 14px/1 Inter,sans-serif;background:var(--s1)}
.search kbd{margin-left:auto;font:500 11px/1 'IBM Plex Mono',monospace;padding:3px 6px;border:1px solid var(--hair2);border-radius:4px;color:var(--ink3)}
.live{display:inline-flex;align-items:center;gap:8px;font:500 13px/1 Inter,sans-serif;color:var(--ink2)}
.live i{width:8px;height:8px;border-radius:50%;background:#F2A63B;box-shadow:0 0 0 4px rgba(242,166,59,.2)}
.pagehead{display:flex;align-items:flex-end;justify-content:space-between;gap:16px}
.pagehead h1{margin:0;font:700 26px/1.2 Archivo,sans-serif;letter-spacing:-.02em}
/* filters */
.filters{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
.segctl{display:flex;border:1px solid var(--hair2);border-radius:8px;overflow:hidden;background:var(--s1)}
.segctl>span{height:34px;display:flex;align-items:center;gap:6px;padding:0 12px;font:500 13px/1 Inter,sans-serif;color:var(--ink2);border-right:1px solid var(--hair)}
.segctl>span:last-child{border-right:0}
.segctl .on{background:var(--wash);color:var(--brandink)}
.chip{height:34px;display:inline-flex;align-items:center;gap:6px;padding:0 12px;border:1px solid var(--hair2);border-radius:999px;font:500 13px/1 Inter,sans-serif;color:var(--ink2);background:var(--s1)}
.chip.on{background:var(--wash);border-color:transparent;color:var(--brandink)}
/* kpis */
.kpis{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));background:var(--s1);border:1px solid var(--hair);border-radius:12px}
.kpi{padding:18px 20px;display:flex;flex-direction:column;gap:8px;border-right:1px solid var(--hair);color:inherit}
.kpi:last-child{border-right:0}
.kk{font:500 13px/1 Inter,sans-serif;color:var(--ink2)}
.kv{font-size:30px;font-weight:600;line-height:1;letter-spacing:-.02em}
.kd{display:flex;gap:8px;align-items:center;font:500 12px/1 Inter,sans-serif}
.kpi.alert{background:var(--warnbg);border-radius:0 11px 11px 0}
.go{margin-top:auto;display:flex;align-items:center;font:500 13px/1 Inter,sans-serif;color:var(--brand)}
/* panels */
.grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:20px}
.panel{background:var(--s1);border:1px solid var(--hair);border-radius:12px;padding:20px 22px;display:flex;flex-direction:column;gap:16px;min-width:0}
.ph{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}
.ph h3{margin:0;font:600 16px/1.25 Archivo,sans-serif;letter-spacing:-.01em}
.tv{display:inline-flex;align-items:center;gap:4px;font:500 12px/1 Inter,sans-serif;color:var(--ink3);white-space:nowrap}
.tv .ic{font-size:16px}
.c4{grid-column:span 4}.c5{grid-column:span 5}.c6{grid-column:span 6}.c7{grid-column:span 7}.c8{grid-column:span 8}.c12{grid-column:span 12}
.sechd{grid-column:span 12;display:flex;align-items:baseline;gap:12px;margin-top:12px}
.sechd h2{margin:0;font:700 19px/1.2 Archivo,sans-serif;letter-spacing:-.015em}
.legend{display:flex;flex-wrap:wrap;gap:14px;align-items:center}
.lg{display:inline-flex;align-items:center;gap:6px;font:400 12px/1 Inter,sans-serif;color:var(--ink2)}
.lg b{width:14px;height:2px;border-radius:2px;display:inline-block}
.lg i{width:10px;height:10px;border-radius:3px;display:inline-block}
.tip{position:absolute;padding:10px 12px;background:var(--s1);border:1px solid var(--hair2);border-radius:8px;box-shadow:0 8px 24px rgba(14,17,22,.10);display:flex;flex-direction:column;gap:6px;min-width:150px}
.tip .tr{display:flex;align-items:center;gap:8px;font:400 12px/1 Inter,sans-serif;color:var(--ink2)}
.tip .tr b{width:12px;height:2px;border-radius:2px}
.tip .tr strong{margin-left:auto;font:600 13px/1 'IBM Plex Mono',monospace;color:var(--ink)}
.rank{display:flex;flex-direction:column;gap:10px}
.rrow{display:grid;grid-template-columns:120px minmax(0,1fr) 56px;align-items:center;gap:12px}
.rk{font:400 13px/1.2 Inter,sans-serif;color:var(--ink2)}
.rv{font-size:13px;text-align:right;color:var(--ink)}
.rtrack{height:14px;background:transparent}
.rtrack.tall{height:22px;background:var(--s3);border-radius:0 4px 4px 0}
.frow{display:flex;flex-direction:column;gap:5px}
.fhead{display:flex;justify-content:space-between}
.seg{display:flex;gap:2px;height:44px;border-radius:6px;overflow:hidden}
.heat{display:grid;grid-template-columns:56px repeat(6,minmax(0,1fr));gap:2px;align-items:center}
.hh{font:500 11px/1 Inter,sans-serif;color:var(--ink3);text-align:center;padding-bottom:4px}
.hl{font:400 12px/1 'IBM Plex Mono',monospace;color:var(--ink2)}
.hc{height:28px;border-radius:4px;display:flex;align-items:center;justify-content:center;font:500 11px/1 'IBM Plex Mono',monospace}
.hc.empty{background:transparent;border:1px dashed var(--hair)}
.meter{height:10px;border-radius:999px;background:var(--s3);overflow:hidden}
.meter div{height:100%;border-radius:999px}
.big{font:600 28px/1 'IBM Plex Mono',monospace;letter-spacing:-.02em}
/* live */
.lcard{border:1px solid var(--hair);border-radius:10px;padding:14px 16px;display:flex;flex-direction:column;gap:10px;background:var(--s2)}
.lhead{display:flex;justify-content:space-between;align-items:center}
.badge{display:inline-flex;align-items:center;gap:4px;height:22px;padding:0 8px;border-radius:999px;background:rgba(242,166,59,.16);color:#9A5B12;font:600 11px/1 Inter,sans-serif}
.dark .badge,.rail .badge{color:#F2C27A}
.badge .ic{font-size:14px}
.lname{font:600 15px/1.25 Archivo,sans-serif}
.lrow{display:flex;justify-content:space-between;align-items:flex-end;gap:12px}
.lplayer{font:500 14px/1.2 Inter,sans-serif}
.bid{font-size:20px;font-weight:600;color:#9A5B12}
.dark .bid,.rail .bid{color:#F2A63B}
.lfoot{display:flex;flex-wrap:wrap;gap:6px 16px;font:400 12px/1 Inter,sans-serif;color:var(--ink2);padding-top:10px;border-top:1px solid var(--hair)}
.lfoot .ic{font-size:15px;margin-right:4px}
.lfoot b{color:var(--ink);font-weight:600}
.lead{margin-left:auto}
.feed{display:flex;flex-direction:column}
.fi{display:grid;grid-template-columns:76px minmax(0,1fr);gap:10px;padding:10px 0;border-top:1px solid var(--hair)}
.fi:first-child{border-top:0}
.fi .ic.warn{font-size:18px}
.ft{font-size:11px;color:var(--ink3);padding-top:2px}
.fa{font:500 13px/1.3 Inter,sans-serif}
.health{display:flex;flex-direction:column}
.hrow{display:grid;grid-template-columns:24px 140px minmax(0,1fr);gap:8px;align-items:center;padding:9px 0;border-top:1px solid var(--hair);font:400 13px/1.3 Inter,sans-serif}
.hrow:first-child{border-top:0}
.hk{color:var(--ink);font-weight:500}.hv{color:var(--ink2)}
/* option b */
.rail{--canvas:#0A0F14;--s1:#10161C;--s2:#161E25;--s3:#1D262E;--hair:#232D36;--hair2:#33404B;--ink:#F2F5F3;--ink2:#A7B2AC;--ink3:#74807A;--okc:#3FBF8F;--warnc:#F2C27A;--warnbg:rgba(242,166,59,.14);background:#0A0F14;color:var(--ink);width:400px;flex:none;padding:24px 22px;display:flex;flex-direction:column;gap:18px}
.rail h3{margin:0;font:600 15px/1.2 Archivo,sans-serif}
.story{display:grid;grid-template-columns:260px minmax(0,1fr);gap:32px;padding:28px 0;border-top:1px solid var(--hair)}
.story h2{margin:0 0 8px;font:700 20px/1.2 Archivo,sans-serif;letter-spacing:-.015em}
.story p{margin:0;font:400 14px/1.6 Inter,sans-serif;color:var(--ink2);text-wrap:pretty}
.story p b{color:var(--ink);font-weight:600}
.strip{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:0;border-top:2px solid var(--ink);padding-top:16px}
.strip .kpi{border-right:0;padding:0 20px 0 0}
.topnav{display:flex;align-items:center;gap:4px}
.topnav>span{height:34px;display:flex;align-items:center;gap:6px;padding:0 12px;border-radius:8px;font:500 14px/1 Inter,sans-serif;color:var(--ink2)}
.topnav .on{background:var(--wash);color:var(--brandink)}
/* phone */
.phone{width:390px;border-radius:28px}
.pbar{height:56px;display:flex;align-items:center;justify-content:space-between;padding:0 16px;background:var(--s1);border-bottom:1px solid var(--hair)}
.pbody{padding:16px;display:flex;flex-direction:column;gap:14px}
.hscroll{display:flex;gap:8px;overflow:hidden}
.pk{display:grid;grid-template-columns:1fr 1fr;gap:0;background:var(--s1);border:1px solid var(--hair);border-radius:12px}
.pk .kpi{border-right:1px solid var(--hair);border-bottom:1px solid var(--hair);padding:14px}
.pk .kpi:nth-child(2n){border-right:0}
.pk .kv{font-size:24px}
.tabs{height:60px;display:flex;justify-content:space-around;align-items:center;background:var(--s1);border-top:1px solid var(--hair)}
.tabs>span{display:flex;flex-direction:column;align-items:center;gap:4px;font:500 11px/1 Inter,sans-serif;color:var(--ink3)}
.tabs .on{color:var(--brand)}
`

// ---------- option 1a: ledger ----------
const W1 = 1440
const chartW = 556
const growthChart = (dark) => `<div style="position:relative">${lineChart({ series: [newUsers], w: chartW, h: 170, max: 10, colors: [dark ? '#199E70' : '#12936A'], ticks: [0, 5, 10], hoverAt: 14, dark })}
<div class="tip" style="left:${36 + 14 * (chartW / 29) + 14}px;top:8px"><span class="sub">Thu 6 Nov</span><div class="tr"><b style="background:${dark ? '#199E70' : '#12936A'}"></b>New users<strong>8</strong></div><div class="tr"><b style="background:var(--ink3)"></b>Same day, previous period<strong>3</strong></div></div></div>`

const engageChart = (dark, w = 500) => {
  const c = dark ? ['#199E70', '#3987E5', '#D95926'] : ['#12936A', '#2A78D6', '#C2410C']
  return legend([[c[0], 'Daily active'], [c[1], 'Weekly active'], [c[2], 'Monthly active']]) + lineChart({ series: [dau, wau, mau], w, h: 170, max: 700, colors: c, labels: ['Daily', 'Weekly', 'Monthly'], ticks: [0, 350, 700], dark })
}

function overviewBody(dark) {
  const c = dark ? ['#199E70', '#3987E5', '#D95926'] : ['#12936A', '#2A78D6', '#C2410C']
  return `
${kpiRow(dark)}
<div class="grid">
  <div class="sechd"><h2>Live now</h2>${liveDot('2 auctions running')}</div>
  ${panel('Running auctions', 'Updates every 5 s while this tab is open', `<div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">${liveCards(dark)}</div>`, 'c8')}
  ${panel('Activity', 'Anonymised, last 50 events', feedList(4), 'c4')}
  <div class="sechd"><h2>Growth</h2><span class="sub">23 Oct to 21 Nov</span></div>
  ${panel('New users a day', `${totalNew} in 30 days, ${totalNew - prevNew} more than the 30 days before`, growthChart(dark), 'c7')}
  ${panel('Sign-up funnel', 'Where people stop between the code and their first league', funnelBars(dark), 'c5')}
  <div class="sechd"><h2>Engagement</h2></div>
  ${panel('Active users', 'Counted once per person per day', engageChart(dark), 'c7')}
  ${panel('Retention by sign-up week', 'Share of each week\'s new users who came back', heatmap(), 'c5')}
  <div class="sechd"><h2>Leagues and auctions</h2></div>
  ${panel('Leagues by status', 'Right now', segmented(), 'c6')}
  ${panel('Auctions completed a week', 'Last 8 weeks', columns(auctionsWeek, { h: 110, color: c[0], width: 500 }) + '<div style="display:flex;justify-content:space-between" class="sub"><span>29 Sep</span><span>17 Nov</span></div>', 'c6')}
  ${panel('Users by district', 'Kerala, top 5 and the rest', rankedBars(districts, { color: '#2E9070' }), 'c6')}
  ${panel('SMS codes today', 'Daily cap set on the backend', `<div class="fx" style="display:flex;align-items:baseline;gap:8px"><span class="big">214</span><span class="sub">of 2,000</span></div>${meter(214, 2000, 'Resets at midnight India time. At the cap, new sign-ins wait until then.')}`, 'c6')}
  <div class="sechd"><h2>Trust and safety</h2></div>
  ${panel('Reports opened and closed', 'Last 14 days', legend([[c[2], 'Opened'], [c[0], 'Closed']]) + pairedColumns(opened, closed, { h: 110, ca: c[2], cb: c[0], width: 410 }) + '<div style="display:flex;justify-content:space-between" class="sub"><span>8 Nov</span><span>21 Nov</span></div>', 'c5')}
  ${panel('Time to close', 'Median, last 30 days', `<div style="display:flex;align-items:baseline;gap:8px"><span class="big">6 h</span><span class="sub">target 24 h</span></div>${meter(6, 24, '2 suspensions so far. 1 image removed this month.')}`, 'c3" style="grid-column:span 3')}
  ${panel('Open by reason', '3 open', rankedBars(reasons, { color: c[2], max: 3 }), 'c4')}
  <div class="sechd"><h2>System health</h2></div>
  ${panel('Checks', 'Refreshed every minute', healthList(), 'c12')}
</div>`
}

const opt1a = `<div class="app" style="width:${W1}px;display:flex" data-screen-label="1a Overview, ledger">
<aside class="side">
  <div class="brandrow"><span class="logo">C</span><span class="wm">Crichere<small>Admin</small></span></div>
  <span class="nav on">${icon('monitoring')}Overview</span>
  <span class="nav">${icon('flag')}Reports<span class="cnt">3</span></span>
  <span class="nav">${icon('emoji_events')}Leagues</span>
  <span class="nav">${icon('group')}Users</span>
  <span class="nav">${icon('block')}Suspended</span>
  <span class="nav">${icon('history')}Audit log</span>
  <div class="sidefoot"><span class="sub" style="display:flex;align-items:center;gap:6px"><span class="ic warn" style="font-size:16px">error</span>1 health warning</span><div class="who"><span class="av">AV</span><div class="col"><span style="font:500 13px/1.2 Inter">aranyventures</span><span class="sub">Sign out</span></div></div></div>
</aside>
<div style="flex:1;min-width:0;display:flex;flex-direction:column">
  <div class="topbar"><div class="search">${icon('search')}Search leagues and people<kbd>/</kbd></div><span style="margin-left:auto">${liveDot()}</span></div>
  <main style="padding:28px 32px 40px;display:flex;flex-direction:column;gap:20px">
    <div class="pagehead"><div class="col" style="gap:6px"><h1>Overview</h1><span class="sub">Friday 21 November, India time</span></div></div>
    ${filterRow(false)}
    ${overviewBody(false)}
  </main>
</div></div>`

// ---------- option 1b: story + live rail ----------
const opt1b = `<div class="app" style="width:${W1}px;display:flex;flex-direction:column" data-screen-label="1b Overview, story with live rail">
<div class="topbar"><div class="brandrow" style="padding:0"><span class="logo">C</span><span class="wm">Crichere</span></div>
  <nav class="topnav"><span class="on">Overview</span><span>Reports <b class="num warnink">3</b></span><span>Leagues</span><span>Users</span><span>Suspended</span><span>Audit log</span></nav>
  <div class="search" style="margin-left:auto;max-width:300px">${icon('search')}Search<kbd>/</kbd></div><span class="av">AV</span></div>
<div style="display:flex">
  <main style="flex:1;min-width:0;padding:28px 36px 40px;display:flex;flex-direction:column;gap:22px">
    <div class="pagehead"><h1>Overview</h1></div>
    ${filterRow(false)}
    <div class="strip">${kpis.map(k => `<div class="kpi"><span class="kk">${k.k}</span><span class="kv num" style="font-size:36px">${k.v}</span><span class="up">${icon('arrow_upward')}${k.d}</span><span class="sub">${k.dd}</span></div>`).join('')}<div class="kpi"><span class="kk">Open reports</span><span class="kv num warnink" style="font-size:36px">3</span><span class="warnink" style="font:500 12px/1 Inter;display:flex;gap:4px;align-items:center">${icon('schedule')}Oldest 19 h</span><span class="sub">Target 24 h</span></div></div>
    <div class="story"><div><h2>Growth</h2><p><b>${totalNew} people</b> joined in the last 30 days, <b>${totalNew - prevNew} more</b> than the month before. Most stop between signing in and joining a league: <b>53%</b> make that step.</p></div><div style="display:flex;flex-direction:column;gap:20px">${growthChart(false)}${funnelBars(false)}</div></div>
    <div class="story"><div><h2>Engagement</h2><p><b>142 people</b> used Crichere today. About <b>1 in 4</b> monthly users comes back on a given day. Weekend match days peak.</p></div><div style="display:flex;flex-direction:column;gap:20px">${engageChart(false, 520)}${heatmap()}</div></div>
    <div class="story"><div><h2>Leagues and auctions</h2><p><b>37 leagues</b> are active. Alappuzha and Ernakulam hold <b>45%</b> of users.</p></div><div style="display:flex;flex-direction:column;gap:20px">${segmented()}${rankedBars(districts, { color: '#2E9070' })}</div></div>
    <div class="story"><div><h2>Trust and safety</h2><p>Reports close in a median of <b>6 hours</b>, inside the 24-hour target.</p></div><div style="display:flex;flex-direction:column;gap:14px">${legend([['#C2410C', 'Opened'], ['#12936A', 'Closed']])}${pairedColumns(opened, closed, { h: 100, ca: '#C2410C', cb: '#12936A', width: 600 })}</div></div>
  </main>
  <aside class="rail">
    <div style="display:flex;justify-content:space-between;align-items:center"><h3>Live now</h3>${liveDot('3 s ago')}</div>
    ${liveCards(true)}
    <h3 style="margin-top:6px">Needs you</h3>
    ${reportsList()}
    <h3 style="margin-top:6px">Activity</h3>
    ${feedList()}
    <h3 style="margin-top:6px">System</h3>
    ${healthList()}
  </aside>
</div></div>`

// ---------- option 1c: phone, dark ----------
const opt1c = `<div class="app dark phone" style="display:flex;flex-direction:column" data-screen-label="1c Overview, phone dark">
<div class="pbar"><div class="brandrow" style="padding:0"><span class="logo">C</span><span class="wm">Overview</span></div>${liveDot('3 s')}</div>
<div class="pbody">
  <div class="hscroll"><div class="segctl"><span>Today</span><span class="on">30 d</span><span>90 d</span></div><span class="chip">Kerala${icon('expand_more')}</span></div>
  <a class="panel" style="color:inherit;flex-direction:row;align-items:center;gap:12px;background:var(--warnbg);border-color:transparent;padding:14px 16px"><span class="ic warn">flag</span><div class="col" style="flex:1"><span style="font:600 14px/1.2 Inter">3 open reports</span><span class="sub">Oldest 19 h, target 24 h</span></div><span class="ic" style="color:var(--ink2)">chevron_right</span></a>
  <div class="pk">${kpis.map(k => `<div class="kpi"><span class="kk">${k.k}</span><span class="kv num">${k.v}</span><span class="up">${icon('arrow_upward')}${k.d}</span></div>`).join('')}</div>
  ${liveCards(true).split('</div><div class="lcard">')[0]}</div>
  ${panel('New users a day', `${totalNew} in 30 days`, lineChart({ series: [newUsers], w: 260, h: 120, max: 10, colors: ['#199E70'], ticks: [0, 10], dark: true }))}
  ${panel('Sign-up funnel', 'Last 30 days', funnelBars(true))}
</div>
<div class="tabs"><span class="on">${icon('monitoring')}Overview</span><span>${icon('flag')}Reports</span><span>${icon('emoji_events')}Leagues</span><span>${icon('group')}Users</span><span>${icon('more_horiz')}More</span></div>
</div>`

window.P15 = { css, body: `<section class="dv-turn" id="t1">
  <div class="dv-thd"><a class="dv-tid" href="#t1">1</a><span class="dv-tname">P15 Admin web, Overview: three directions</span></div>
  <p class="intro">Batch 15. The owner's private admin site (docs/PHASE20.md). Daylight by default, Floodlight tokens for dark. Charts use the validated admin palette: categorical #12936A, #2A78D6, #C2410C (dark #199E70, #3987E5, #D95926), the brand ramp for amounts and the heatmap, status colours only with an icon and a word. Amber appears only on the live indicator, live badge and current bid. Sample data is fictional and small on purpose: early numbers must look honest. Each chart has a Table switch and a hover readout (shown once, on New users a day).</p>
  <div class="dv-opts">
    <div class="dv-opt" id="1a"><div class="dv-olabel"><a class="dv-oid" href="#1a">1a</a>Ledger: sidebar, KPI strip, sections in a 12-column grid. By the book, densest.</div>${opt1a}
      <p class="note">KPI tiles share one hairline panel instead of five cards. Open reports is the only tinted tile, because it is the only one that asks for action. Sections follow the order of the daily question: live, growth, engagement, leagues, safety, system.</p></div>
    <div class="dv-opt" id="1b"><div class="dv-olabel"><a class="dv-oid" href="#1b">1b</a>Story with a live rail: each section opens with one plain sentence; live, reports and system stay pinned in a Floodlight rail.</div>${opt1b}
      <p class="note">The sentences are generated from the same numbers as the charts, so the page reads in ten seconds before any chart is studied. The dark rail borrows the auction console's register: everything live lives there and nowhere else.</p></div>
    <div class="dv-opt" id="1c"><div class="dv-olabel"><a class="dv-oid" href="#1c">1c</a>Phone, dark: the morning check.</div>${opt1c}
      <p class="note" style="max-width:390px">Reports first, then the KPI grid, the busiest live auction, then charts one per screen width. Same tokens as 1a in Floodlight.</p></div>
  </div>
  <p class="dv-next">Try next: "1b's story sentences on 1a's grid", "show the reports queue and report detail in the chosen direction", "dark version of the chosen desktop".</p>
</section>` }
})()
