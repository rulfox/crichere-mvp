(window.P16 = window.P16 || []).push({ o: 1, build() {
const A = window.AK, icon = A.icon, inr = A.inr
const newUsers = [2, 4, 3, 5, 2, 6, 4, 3, 7, 5, 4, 2, 3, 5, 8, 6, 4, 3, 2, 5, 4, 6, 3, 2, 4, 5, 3, 2, 1, 3]
const dau = [96, 104, 99, 112, 131, 140, 118, 101, 108, 115, 109, 126, 149, 152, 120, 110, 117, 121, 113, 138, 158, 161, 127, 119, 124, 130, 122, 141, 166, 142]
const wau = dau.map((v, i) => Math.round(360 + i * 2.4 + (v - 120) * 0.6)), mau = dau.map((v, i) => Math.round(560 + i * 1.7))
const funnel = [['Code sent', 1920], ['Signed in', 1544], ['Profile done', 1302], ['Joined a league', 688]]
const status = [['Draft', 9], ['Registration open', 14], ['Auction live', 2], ['Auction done', 8], ['Completed', 4]]
const cohorts = [['12 Oct', [100, 58, 44, 39, 35, 33]], ['19 Oct', [100, 61, 47, 40, 37]], ['26 Oct', [100, 55, 42, 38]], ['2 Nov', [100, 63, 49]], ['9 Nov', [100, 60]], ['16 Nov', [100]]]
const districts = [['Alappuzha', 312], ['Ernakulam', 268], ['Kottayam', 190], ['Thrissur', 141], ['Kozhikode', 97], ['Other districts', 276]]
const live = [['Vembanad Premier League 2026', 14, 40, 'Arjun Pillai', 45000, 'Backwater Blasters', 6, 38], ['Kuttanad Cup', 3, 24, 'Rahul Joseph', 12500, 'Punnamada Pirates', 2, 11]]
const feed = [['2 min ago', 'Player sold for 38,000 pts', 'Vembanad Premier League 2026'], ['6 min ago', 'New league created', 'Kottayam'], ['11 min ago', 'Auction started', 'Kuttanad Cup'], ['18 min ago', '4 players joined', 'Thrissur Weekend Bash']]
const health = [['ok', 'Nightly summary', 'Ran 00:10, took 41 s'], ['ok', 'Sign-in codes', '3.1% failed today'], ['ok', 'SMS today', '214 of 2,000'], ['warn', 'MSG91 balance', '8,420 credits, about 18 days left at this rate'], ['ok', 'Alert emails', 'No bounces in 30 days']]
const kpis = [['Users', '1,284', '+116 new', '+61 vs previous 30 days', newUsers], ['Active today', '142', '23% of monthly', 'Monthly active 610', dau], ['Active leagues', '37', '+5', '2 auctions live now', [28, 29, 31, 30, 32, 33, 35, 37]], ['Auctions completed', '12', '+4', 'Last 30 days', [1, 0, 2, 1, 3, 2, 1, 2]]]
const liveCard = l => `<div class="lcard"><div class="lhead">${A.st('live', 'Live')}<span class="sub">Alappuzha</span></div><span class="lname">${l[0]}</span><div class="lrow"><div class="col"><span class="sub">Lot ${l[1]} of ${l[2]}</span><span class="strong">${l[3]}</span></div><div class="col end"><span class="sub">Current bid</span><span class="bid num">${inr(l[4])} pts</span></div></div><div class="lfoot"><span>${icon('gavel')}<b class="num">${l[6]}</b> bids a minute</span><span>${icon('visibility')}<b class="num">${l[7]}</b> watching</span><span style="margin-left:auto">Leading: ${l[5]}</span></div></div>`
const sec = (t, s) => `<div class="sechd"><div class="row"><h2>${t}</h2></div>${A.say(s)}</div>`

function body(theme) {
  const c = A.c(theme)
  const tip = `<div class="tip" style="left:${36 + 14 * (556 / 29) + 14}px;top:8px"><span class="sub">Thu 6 Nov</span><div class="tr"><b style="background:${c[0]}"></b>New users<strong>8</strong></div><div class="tr"><b style="background:var(--ink3)"></b>Same day, previous period<strong>3</strong></div></div>`
  return A.filters([A.seg(['Today', '7 days', '30 days', '90 days', 'Custom'], '30 days'), A.chip('Compare to previous 30 days', false, 'compare_arrows'), A.chip('State: Kerala', false, 'expand_more'), A.chip('District: All', false, 'expand_more'), A.chip('Internal accounts hidden', true, 'check')]) +
  `<div class="kpis">${kpis.map(k => `<div class="kpi"><span class="kk">${k[0]}</span><span class="kv num">${k[1]}</span><span class="up">${icon('arrow_upward')}${k[2]}</span>${A.spark(k[4], c[0])}<span class="sub">${k[3]}</span></div>`).join('')}<div class="kpi alert"><span class="kk">Open reports</span><span class="kv num">3</span><span class="warn" style="font:500 12px/1 Inter;display:flex;gap:4px;align-items:center">${icon('schedule')}Oldest 19 h</span><span class="sub">Target: handled within 24 h</span><span class="go" style="margin-top:auto">Open reports queue${icon('chevron_right')}</span></div></div>
  <div class="grid">
  ${sec('Live now', '<b>2 auctions</b> are running with <b>49 people</b> watching. Bids are coming in at 8 a minute.')}
  ${A.panel('Running auctions', 'Updates every 5 s while this tab is open', `<div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">${live.map(liveCard).join('')}</div>`, 'c8', '')}
  ${A.panel('Activity', 'Anonymised, last 50 events', `<div class="feed">${feed.map(f => `<div class="fi"><span class="ft num">${f[0]}</span><div class="col"><span class="fa">${f[1]}</span><span class="sub">${f[2]}</span></div></div>`).join('')}</div>`, 'c4', '')}
  ${sec('Growth', '<b>116 people</b> joined in the last 30 days, <b>61 more</b> than the 30 days before. The biggest drop is after sign-in: <b>53%</b> of people with a profile go on to join a league.')}
  ${A.panel('New users a day', '23 Oct to 21 Nov', `<div style="position:relative">${A.line({ series: [newUsers], w: 556, h: 170, max: 10, colors: [c[0]], ticks: [0, 5, 10], hoverAt: 14, theme })}${tip}</div>`, 'c7')}
  ${A.panel('Sign-up funnel', 'Last 30 days', A.funnel(funnel), 'c5')}
  ${sec('Engagement', '<b>142 people</b> used Crichere today. About <b>1 in 4</b> monthly users comes back on a given day, and match weekends peak.')}
  ${A.panel('Active users', 'Counted once per person per day', A.legend([[c[0], 'Daily active'], [c[1], 'Weekly active'], [c[2], 'Monthly active']]) + A.line({ series: [dau, wau, mau], w: 500, h: 170, max: 700, colors: c, labels: ['Daily', 'Weekly', 'Monthly'], ticks: [0, 350, 700], theme }), 'c7')}
  ${A.panel('Retention by sign-up week', 'Share of each week\'s new users who came back', A.heat(cohorts), 'c5')}
  ${sec('Leagues and auctions', '<b>37 leagues</b> are active. Alappuzha and Ernakulam hold <b>45%</b> of users; Kozhikode is the fastest-growing district this month.')}
  ${A.panel('Leagues by status', 'Right now, 37 leagues', A.segbar(status, ['#C6E3D8', '#5FB294', '#F2A63B', '#2E9070', '#084C38'], ['#0E1116', '#0E1116', '#0E1116', '#FFF', '#FFF']), 'c6')}
  ${A.panel('Auctions completed a week', 'Last 8 weeks', A.columns([1, 0, 2, 1, 3, 2, 1, 2], { h: 110, color: c[0], width: 500, x: ['29 Sep', '17 Nov'] }), 'c6')}
  ${A.panel('Users by district', 'Kerala, top 5 and the rest', A.ranked(districts, { color: '#2E9070' }), 'c6')}
  ${A.panel('SMS codes today', 'Daily cap set on the backend', `<div class="row" style="align-items:baseline"><span class="big">214</span><span class="sub">of 2,000</span></div>${A.meter(214, 2000, 'Resets at midnight India time. At the cap, new sign-ins wait until then.')}`, 'c6')}
  ${sec('Trust and safety', 'Reports close in a median of <b>6 hours</b>, inside the 24-hour target. <b>3</b> are open now.')}
  ${A.panel('Reports opened and closed', 'Last 14 days', A.legend([[c[2], 'Opened'], [c[0], 'Closed']]) + A.paired([1, 0, 2, 1, 0, 3, 1, 0, 1, 2, 0, 1, 2, 1], [0, 1, 1, 2, 0, 1, 2, 1, 0, 1, 1, 1, 1, 2], { h: 110, ca: c[2], cb: c[0], width: 410, x: ['8 Nov', '21 Nov'] }), 'c5')}
  ${A.panel('Time to close', 'Median, last 30 days', `<div class="row" style="align-items:baseline"><span class="big">6 h</span><span class="sub">target 24 h</span></div>${A.meter(6, 24, '2 accounts suspended so far, 1 image removed this month.')}`, 'c3')}
  ${A.panel('Open by reason', '3 open', A.ranked([['Inappropriate image', 1], ['Spam or misleading', 1], ['Something else', 1]], { color: c[2], max: 3 }), 'c4')}
  ${sec('System health', 'Everything is working. <b>MSG91 credit</b> runs out in about 18 days at this rate.')}
  ${A.panel('Checks', 'Refreshed every minute', `<div>${health.map(h => `<div class="hrow">${icon(h[0] === 'ok' ? 'check_circle' : 'error', h[0])}<span class="strong">${h[1]}</span><span style="color:var(--ink2)">${h[2]}</span></div>`).join('')}</div>`, 'c12', '')}
  </div>`
}
const desk = theme => A.frame({ theme, title: 'Overview', sub: 'Friday 21 November, India time', body: body(theme), label: `O${theme === 'dark' ? 2 : 1} Overview ${theme}` })
const phone = A.phone({ title: 'Overview', right: A.liveDot('3 s'), label: 'O3 Overview phone light', body:
  `<div class="row">${A.seg(['Today', '30 d', '90 d'], '30 d')}${A.chip('Kerala', false, 'expand_more')}</div>` +
  `<div class="panel" style="flex-direction:row;align-items:center;gap:12px;background:var(--warnbg);border-color:transparent;padding:14px 16px">${icon('flag', 'warn')}<div class="col" style="flex:1"><span style="font:600 14px/1.2 Inter">3 open reports</span><span class="sub">Oldest 19 h, target 24 h</span></div>${icon('chevron_right', 'muted')}</div>` +
  A.say('<b>116 people</b> joined in 30 days, <b>61 more</b> than before. <b>2 auctions</b> are live.') +
  `<div class="pk">${kpis.map(k => `<div class="kpi"><span class="kk">${k[0]}</span><span class="kv num">${k[1]}</span><span class="up">${icon('arrow_upward')}${k[2]}</span></div>`).join('')}</div>` + liveCard(live[0]) +
  A.panel('New users a day', '116 in 30 days', A.line({ series: [newUsers], w: 260, h: 120, max: 10, colors: [A.C.light[0]], ticks: [0, 10], theme: 'light' })) })
return A.section('O', 'O · Overview, final direction', 'Owner pick 2026-11-21: 1a grid with 1b\'s one-line summary under each section heading. Theme follows the device; dark shown in O2, phone dark in P15 1c.', [
  A.board('O1', 'Desktop 1440, light', desk('light'), 'The summary sentence is generated from the same numbers as the charts beneath it, so it can never disagree with them. Bold carries only the figures. Every chart keeps the Table switch and hover readout (shown on New users a day).'),
  A.board('O2', 'Desktop 1440, dark (device in dark mode)', desk('dark'), 'Floodlight tokens and the dark chart steps (#199E70, #3987E5, #D95926), validated against #10161C. Status tints are composited on the dark surface, never reused from light.'),
  A.board('O3', 'Phone 390, light', phone, 'Same order as 1c: reports first, then one sentence, the KPI grid, the busiest live auction and one chart per row.')])
} })
