(window.P16 = window.P16 || []).push({ o: 2, build() {
const A = window.AK, icon = A.icon
// ---------- Direction A: floodlit ground (owner pick 2026-11-21). CSS shapes + brand assets only. ----------
const css = `<style>
.signin{display:grid;grid-template-columns:864px 576px;height:820px}
.scene{position:relative;overflow:hidden;background:#0A0F14;isolation:isolate}
.scene .sky{position:absolute;inset:0;background:radial-gradient(130% 90% at 50% 118%,#0E4A36 0%,#0A2A20 36%,#0A0F14 72%)}
.scene .beam{position:absolute;top:70px;width:620px;height:760px;background:linear-gradient(165deg,rgba(232,245,238,.13),rgba(232,245,238,0) 72%);animation:warm 1.4s ease-out both}
.scene .b1{left:34px;clip-path:polygon(0 0,7% 0,100% 100%,38% 100%)}
.scene .b2{right:34px;clip-path:polygon(93% 0,100% 0,62% 100%,0 100%);background:linear-gradient(195deg,rgba(232,245,238,.13),rgba(232,245,238,0) 72%);animation-delay:.2s}
.scene .pylon{position:absolute;top:96px;width:3px;height:300px;background:linear-gradient(#33404B,rgba(16,22,28,0));z-index:1}
.scene .p1{left:52px}.scene .p2{right:52px}
.scene .lamp{position:absolute;left:-19px;top:-26px;width:40px;height:24px;border-radius:3px;background:#1D262E radial-gradient(circle,#F2F5F3 1.6px,transparent 2.2px) 0 0/8px 8px;box-shadow:0 0 22px 6px rgba(232,245,238,.45);animation:warm 1s ease-out both}
.scene .p2 .lamp{animation-delay:.2s}
.scene .glow{position:absolute;width:560px;height:560px;border-radius:50%;background:radial-gradient(circle,rgba(232,245,238,.30) 0%,rgba(95,178,148,.12) 32%,transparent 64%);animation:warm 1.4s ease-out both}
.scene .g1{left:-228px;top:-200px}.scene .g2{right:-228px;top:-200px;animation-delay:.2s}
.scene .fld{position:absolute;left:50%;bottom:-430px;width:1120px;height:1120px;transform:translateX(-50%) perspective(1200px) rotateX(64deg);transform-origin:50% 50%}
.scene .bnd{position:absolute;inset:0;border-radius:50%;border:2px solid rgba(242,245,243,.20);background:repeating-linear-gradient(90deg,rgba(255,255,255,.035) 0 56px,rgba(255,255,255,0) 56px 112px),radial-gradient(circle,#15694D 0%,#0E523C 42%,#0B3A2B 68%,#0A2A20 100%)}
.scene .ring{position:absolute;inset:23%;border-radius:50%;border:2px dashed rgba(242,245,243,.16)}
.scene .pitch{position:absolute;left:50%;top:50%;width:6.5%;height:24%;transform:translate(-50%,-50%);background:linear-gradient(rgba(201,216,207,.20),rgba(201,216,207,.12));border-radius:2px}
.scene .pitch i{position:absolute;left:-30%;right:-30%;height:2px;background:rgba(255,255,255,.55)}
.scene .pitch i:first-child{top:9%}.scene .pitch i:last-child{bottom:9%}
.scene .stumps{position:absolute;left:50%;top:600px;width:210px;transform:translateX(-50%);z-index:2;filter:drop-shadow(0 10px 18px rgba(0,0,0,.45))}
.scene .grain{position:absolute;inset:0;opacity:.09;mix-blend-mode:screen;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");z-index:3;pointer-events:none}
.scene .scopy{position:absolute;left:104px;top:132px;max-width:560px;z-index:4;display:flex;flex-direction:column;gap:20px}
.scene .scopy img{height:40px;width:auto;align-self:flex-start}
.scene .scopy h1{margin:0;font:700 58px/1.02 Archivo,sans-serif;letter-spacing:-.035em;color:#F2F5F3;text-wrap:balance}
.scene .scopy p{margin:0;font:400 17px/1.55 Inter,sans-serif;color:#A7B2AC;max-width:420px}
@keyframes warm{from{opacity:0}to{opacity:1}}
@media (prefers-reduced-motion:reduce){.scene *{animation:none !important}}
.spanel{position:relative;background:var(--canvas);display:flex;flex-direction:column;justify-content:center;gap:20px;padding:0 96px}
.dark .spanel{border-left:1px solid var(--hair)}
.spanel h2{margin:0;font:700 30px/1.15 Archivo,sans-serif;letter-spacing:-.025em}
.spanel .say{max-width:none}
.toplink{position:absolute;top:28px;right:32px}
.env{display:inline-flex;align-items:center;gap:6px;height:28px;padding:0 10px;border:1px solid var(--hair2);border-radius:999px;font:500 12px/1 'IBM Plex Mono',monospace;color:var(--ink2);box-sizing:border-box;align-self:flex-start}
.env .ic{font-size:15px;color:var(--okc)}
.back{display:inline-flex;align-items:center;gap:4px;height:36px;padding:0 4px;font:500 14px/1 Inter,sans-serif;color:var(--ink2)}
.back .ic{font-size:17px}
.gbtn{height:48px;gap:12px;font-size:15px;box-sizing:border-box}
.gmark{width:22px;height:22px;border-radius:50%;border:1px solid var(--hair2);display:flex;align-items:center;justify-content:center;font:700 12px/1 Inter,sans-serif;color:#2A78D6;flex:none}
.spin{width:16px;height:16px;border-radius:50%;border:2px solid var(--hair2);border-top-color:var(--brand);animation:spin .8s linear infinite;flex:none}
@keyframes spin{to{transform:rotate(360deg)}}
@media (prefers-reduced-motion:reduce){.spin{animation:none}}
.gbtn.wait{color:var(--ink2);background:var(--s2)}
.snote{display:flex;gap:8px;align-items:flex-start;font:400 13px/1.5 Inter,sans-serif;color:var(--ink3)}
.snote .ic{font-size:17px;margin-top:1px}
.sfoot{position:absolute;left:96px;right:96px;bottom:28px;display:flex;flex-direction:column;gap:10px;font:400 12px/1.5 Inter,sans-serif;color:var(--ink3)}
.pscene{position:relative;height:250px;flex:none}
.pscene .scene{position:absolute;inset:0}
.scene.compact .fld{width:620px;height:620px;bottom:-290px}
.scene.compact .beam{width:300px;height:300px;top:0}
.scene.compact .pylon{display:none}
.scene.compact .glow{width:300px;height:300px}
.scene.compact .g1{left:-130px;top:-120px}.scene.compact .g2{right:-130px;top:-120px}
.scene.compact .stumps{width:110px;top:150px}
.scene.compact .scopy{left:24px;top:28px;gap:12px;max-width:300px}
.scene.compact .scopy img{height:28px}
.scene.compact .scopy h1{font-size:30px}
.scene.compact .scopy p{display:none}
.pform{position:relative;padding:24px 20px 28px;display:flex;flex-direction:column;gap:18px;background:var(--canvas)}
.pform .toplink{top:18px;right:16px}
.pform h2{margin:0;font:700 24px/1.2 Archivo,sans-serif;letter-spacing:-.02em}
.pform .pfoot{display:flex;flex-direction:column;gap:10px;padding-top:6px;font:400 12px/1.5 Inter,sans-serif;color:var(--ink3)}
</style>`
const scene = compact => `<div class="scene${compact ? ' compact' : ''}" aria-hidden="true"><div class="sky"></div><div class="glow g1"></div><div class="glow g2"></div><div class="beam b1"></div><div class="beam b2"></div><div class="fld"><div class="bnd"></div><div class="ring"></div><div class="pitch"><i></i><i></i></div></div><div class="pylon p1"><i class="lamp"></i></div><div class="pylon p2"><i class="lamp"></i></div><img class="stumps" src="assets/illustrations/floodlight/splash-stumps.svg" alt=""><div class="grain"></div><div class="scopy"><img src="assets/logo/crichere-lockup-reverse.svg" alt="crichere"><h1>From the pavilion.</h1><p>Reports, live auctions and every league, for the Crichere team.</p></div></div>`
const gbtn = (t = 'Sign in with Google') => `<span class="btn full gbtn"><span class="gmark">G</span>${t}</span>`
const note = `<div class="snote">${icon('mail')}<span>Every sign-in sends an email to the account owner, so a sign-in you didn't make shows up straight away.</span></div>`
const link = `<a class="back toplink">crichere.com${icon('arrow_outward')}</a>`
const envChip = (t = 'Production · api.crichere.com') => `<span class="env">${icon('dns')}${t}</span>`
const foot = `<div class="sfoot">${envChip()}<span>Sessions last 30 minutes and end at once when you sign out everywhere.</span></div>`
const desk = (theme, inner, label) => `<div class="app signin${theme === 'dark' ? ' dark' : ''}" style="width:1440px" data-screen-label="${label}">${scene(false)}<div class="spanel">${link}${inner}${foot}</div></div>`
const base = `<h2>Sign in</h2><p class="say">For the Crichere team only. Use the Google account that is on the admin list.</p>`
const s1 = theme => desk(theme, base + gbtn() + note, `S1 Sign in ${theme}`)
const s1w = desk('light', base + `<span class="btn full gbtn wait"><i class="spin"></i>Waiting for Google…</span><div class="snote">${icon('open_in_new')}<span>A Google window is open. Finish there, or close it to cancel.</span></div>`, 'S1w Waiting')
const s2 = desk('light', `<h2>Sign in</h2>${A.banner('danger', 'cancel', '<b>rulfo.dev@gmail.com isn\'t on the admin list.</b> Nothing was opened or saved. Choose a different Google account, or ask the owner to add this one.')}` + gbtn('Use a different Google account'), 'S2 Not allowed')
const phone = theme => `<div class="app phone${theme === 'dark' ? ' dark' : ''}" style="display:flex;flex-direction:column" data-screen-label="S6 phone ${theme}"><div class="pscene">${scene(true)}</div><div class="pform">${link}<h2>Sign in</h2><p class="say">For the Crichere team only. Use the Google account on the admin list.</p>${gbtn()}${note}<div class="pfoot">${envChip('Production')}<span>Sessions last 30 minutes.</span></div></div></div>`
// ---------- unchanged: session ended, account menu, search ----------
const overviewShell = A.frame({ title: 'Overview', sub: 'Friday 21 November, India time', label: 'S3', minH: 760, live: 'Live, updated 3 s ago',
  body: `<div class="fade" style="display:flex;flex-direction:column;gap:20px">${A.filters([A.seg(['Today', '7 days', '30 days', '90 days'], '30 days'), A.chip('Internal accounts hidden', true, 'check')])}${A.imgph('Overview content, held in place', 420)}</div>`,
  overlay: A.dialog('Your session ended', '<p>Sessions last 30 minutes, and end at once when you sign out on another device. Sign in again to carry on from this page; nothing you were looking at is lost.</p>', A.btn('Sign in with Google', 'primary'), 440) })
const menu = `<div class="menu" style="left:16px;bottom:72px"><div class="col" style="padding:10px 10px 8px"><span class="strong">aranyventures@gmail.com</span><span class="sub">Signed in 12 min ago, session ends at 11:42</span></div><hr><div style="padding:6px 10px;display:flex;flex-direction:column;gap:8px"><span class="sub">Theme</span>${A.seg(['Device', 'Light', 'Dark'], 'Device')}</div><hr><span class="mi">${icon('devices')}Active sessions<span class="num sub" style="margin-left:auto">2</span></span><span class="mi">${icon('logout')}Sign out</span><span class="mi" style="color:var(--dangerc)">${icon('phonelink_erase', 'danger')}Sign out everywhere</span></div>`
const s4 = A.frame({ title: 'Overview', sub: 'Friday 21 November, India time', label: 'S4', minH: 760, body: `<div class="fade">${A.imgph('Overview content', 520)}</div>`, overlay: menu })
const results = `<div class="scrim"></div><div class="menu" style="left:50%;transform:translateX(-50%);top:84px;width:640px;padding:10px;gap:4px">${A.input('Vemb', { focus: true, ic: 'search', end: '<kbd style="margin-left:auto">Esc</kbd>' })}<span class="sub" style="padding:10px 10px 4px">Leagues</span><div class="mi" style="height:52px;background:var(--s3)">${A.cellLeague('Vembanad Premier League 2026', 'Alappuzha, auction live', '#0B6E4F')}<span style="margin-left:auto">${A.st('live', 'Live')}</span></div><div class="mi" style="height:52px">${A.cellLeague('Vembanad Veterans Cup', 'Alappuzha, completed', '#2A78D6')}</div><span class="sub" style="padding:10px 10px 4px">People</span><div class="mi" style="height:52px">${A.cellPerson('Vembanad Sports Club', 'Organizer, 2 leagues')}</div><hr><span class="sub" style="padding:4px 10px 6px">Paste a league or user ID to go straight to it. <kbd>↑</kbd> <kbd>↓</kbd> to move, <kbd>Enter</kbd> to open</span></div>`
const s5 = A.frame({ title: 'Overview', label: 'S5', minH: 700, body: `<div class="fade">${A.imgph('Overview content', 480)}</div>`, overlay: results })
return css + A.section('S', 'S · Sign-in (direction A: floodlit ground), session, account menu, search', 'Owner pick 2026-11-21. Only the sign-in screens are rich; every screen after sign-in stays quiet. The scene shows no real data, because the page is public.', [
  A.board('S1', 'Sign in, light (device in light mode)', s1('light'), 'Scene is always Floodlight; the sign-in panel follows the device theme. The link to crichere.com sits in the top-right corner at the standard 32px edge padding (16px on phones). The production chip and session note sit in the footer on the form column. Built from CSS shapes plus the brand lockup and the splash-stumps illustration. One load moment: the floodlights warm up over about 1.4 s; reduced motion shows it still. Decoration is aria-hidden.'),
  A.board('S1d', 'Sign in, dark', s1('dark'), 'The production chip shows which backend this copy talks to, so a local build is never mistaken for production.'),
  A.board('S1w', 'Waiting for Google', s1w, 'The spinner inside the button is one of the places DESIGN.md allows a spinner. Closing the Google window returns the button to S1.'),
  A.board('S2', 'Account not allowed', s2, 'Names the account so the owner sees which one was used. Same copy whether the account exists in Crichere or not.'),
  A.board('S6', 'Phone, light', phone('light'), 'The scene becomes a 250px band; floodlight poles drop out at this size. The crichere.com link sits top right of the form area, level with the heading.'),
  A.board('S6d', 'Phone, dark', phone('dark')),
  A.board('S3', 'Session ended (30 minutes, or signed out elsewhere)', overviewShell, 'Shown over the page, which stays where it was. Signing in returns to the same URL with the same filters.'),
  A.board('S4', 'Account menu: theme, sessions, sign out everywhere', s4, 'Theme defaults to Device and is remembered in this browser. Sign out everywhere ends every admin session at once, including this one.'),
  A.board('S5', 'Search (press /)', s5, 'Searches league and people names, and jumps straight to a pasted ID. Results never show phone numbers; phone lookup lives on the Users page (U2).')])
} })
