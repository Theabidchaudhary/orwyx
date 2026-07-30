/* scripts.js — JS for new pages (work, services, privacy, terms, header, footer).
   Same scope rule as styles.css: code for new pages lives here, not in assets/. */
(function () {
  "use strict";

  // Hamburger menu toggle — must be on window so inline onclick in header.html can call it
  function pauseObservers() {
    if (window._obs) window._obs.disconnect();
    if (window._logoObs) window._logoObs.disconnect();
  }
  function resumeObservers() {
    if (window._obs) window._obs.observe(document.body, { childList: true, subtree: true });
    if (window._logoObs) window._logoObs.observe(document.body, { childList: true, subtree: true });
  }

  window.orwyxToggleMenu = function () {
    var menu = document.getElementById('orwyx-mobile-menu');
    var btn = document.getElementById('orwyx-hamburger');
    if (!menu || !btn) return;
    var lines = btn.querySelectorAll('.ham-line');
    var isOpen = menu.getAttribute('data-open') === '1';

    if (isOpen) {
      // Pause observers so DOM mutations don't cause reflows during close animation
      pauseObservers();
      // Animate hamburger icon back immediately
      lines[0].style.transform = '';
      lines[1].style.opacity = '1';
      lines[2].style.transform = '';
      document.body.style.overflow = '';
      // Play item exit animations first (total ~0.44s: last delay 0.20s + duration 0.22s)
      menu.classList.add('menu-closing');
      setTimeout(function () {
        menu.classList.remove('menu-closing');
        // Now collapse the clip-path
        menu.style.willChange = 'clip-path';
        menu.style.clipPath = 'inset(0 0 100% 0 round 50px)';
        menu.style.pointerEvents = 'none';
        menu.setAttribute('data-open', '0');
        // Clear will-change after transition completes and reconnect observers
        setTimeout(function () {
          menu.style.willChange = '';
          resumeObservers();
        }, 600);
      }, 450);
    } else {
      // Pause observers so DOM mutations don't cause reflows during open animation
      pauseObservers();
      // Promote to compositor layer before animating
      menu.style.willChange = 'clip-path';
      // Force reflow so CSS item animation restarts cleanly on re-open
      void menu.offsetHeight;
      menu.style.clipPath = 'inset(0 0 0% 0 round 50px)';
      menu.style.pointerEvents = 'auto';
      menu.setAttribute('data-open', '1');
      document.body.style.overflow = 'hidden';
      lines[0].style.transform = 'translateY(7px) rotate(45deg)';
      lines[1].style.opacity = '0';
      lines[2].style.transform = 'translateY(-7px) rotate(-45deg)';
      // Clear will-change after animation completes and reconnect observers
      setTimeout(function () {
        menu.style.willChange = '';
        resumeObservers();
      }, 1300);
    }
  };

  function hideReactNavs() {
    document.querySelectorAll('img[src*="amphora"]').forEach(function (img) {
      var el = img;
      for (var i = 0; i < 6; i++) {
        el = el.parentElement;
        if (!el) break;
        var tag = el.tagName && el.tagName.toLowerCase();
        var style = window.getComputedStyle(el);
        if (tag === 'nav' || style.position === 'fixed') { el.style.display = 'none'; break; }
      }
    });
  }

  function inject(id, path) {
    fetch(path)
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var el = document.getElementById(id);
        if (el) el.outerHTML = html;
        if (id === 'orwyx-header') {
          setTimeout(hideReactNavs, 300);
          setTimeout(hideReactNavs, 1500);
        }
      });
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      inject('orwyx-header', '/header.html');
      inject('orwyx-footer', '/footer.html');
    });
  } else {
    inject('orwyx-header', '/header.html');
    inject('orwyx-footer', '/footer.html');
  }
  
  function reveal() {
    var els = Array.prototype.slice.call(document.querySelectorAll(".op-reveal"));
    function animate(el) { if (el.classList.contains("op-anim")) return; el.style.animationDelay = (Math.random() * 0.12).toFixed(2) + "s"; el.classList.add("op-anim"); }
    var vh = window.innerHeight || 800;
    els.forEach(function (el) { if (el.getBoundingClientRect().top < vh * 1.05) animate(el); });
    if (!("IntersectionObserver" in window)) return;
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) { if (en.isIntersecting) { animate(en.target); io.unobserve(en.target); } });
    }, { threshold: 0.05, rootMargin: "0px 0px -40px 0px" });
    els.forEach(function (el) { if (!el.classList.contains("op-anim")) io.observe(el); });
  }

  function workGrid() {
    var grid = document.querySelector("[data-work-grid]");
    if (!grid) return;
    var pills = document.querySelectorAll("[data-filter]");
    var nicheSelect = document.querySelector("[data-niche-select]");
    var cards = Array.prototype.slice.call(grid.querySelectorAll("[data-formats]"));
    var moreBtn = document.querySelector("[data-load-more]");
    var countEl = document.querySelector("[data-work-count]");
    var PAGE = 18;
    var params = new URLSearchParams(location.search);
    var initFormat = params.get("f") || "all";
    var state = { format: initFormat, niche: params.get("niche") || "all", shown: PAGE };
    if (nicheSelect && state.niche !== "all") nicheSelect.value = state.niche;

    function matches(card) {
      var f = card.getAttribute("data-formats") || "";
      var n = (card.getAttribute("data-niches") || "");
      var okFormat = state.format === "all" || f === state.format;
      var okNiche = state.niche === "all" || n.split("|").indexOf(state.niche) >= 0;
      return okFormat && okNiche;
    }
    var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    var OUT_DUR      = 200;
    var EXIT_TOTAL   = 1000;
    var SLIDE_DUR    = 360;
    var STAGGER_IN   = 40;
    var gen          = 0;

    function cancelCards() {
      cards.forEach(function (c) {
        c.classList.remove("op-leaving", "op-entering");
        c.style.opacity = "";
        c.style.transform = "";
        c.style.transition = "";
        c.style.pointerEvents = "";
        c.style.visibility = "";
        c.style.position = "";
        c.style.left = "";
        c.style.top = "";
        c.style.width = "";
        c.style.height = "";
        c.style.overflow = "";
        c.style.margin = "";
        c.style.zIndex = "";
        c.style.boxSizing = "";
        var th = c.querySelector(".op-work-thumb");
        if (th) { th.style.flex = ""; th.style.height = ""; th.style.transition = ""; th.style.minHeight = ""; th.style.aspectRatio = ""; th.style.width = ""; }
      });
      grid.style.minHeight = "";
    }

    function popIn(c) {
      c.style.opacity = "";
      c.style.pointerEvents = "";
      c.style.transform = "";
      c.classList.remove("op-entering");
      void c.offsetWidth;
      c.classList.add("op-entering");
      var done = function () { c.classList.remove("op-entering"); c.removeEventListener("animationend", done); };
      c.addEventListener("animationend", done);
      setTimeout(done, 600);
    }

    function render() {
      gen++;
      var myGen = gen;

      var gridH = grid.getBoundingClientRect().height;
      var before = new Map();
      cards.forEach(function (c) {
        if (c.style.display !== "none") {
          var r = c.getBoundingClientRect();
          var th = c.querySelector(".op-work-thumb");
          before.set(c, { rect: r, thumbH: th ? th.getBoundingClientRect().height : 0 });
        }
      });

      cancelCards();

      var willShow = cards.filter(matches);
      var shownSet = new Set(willShow.slice(0, state.shown));

      var toHide = [], toShow = [], staying = [];
      cards.forEach(function (c) {
        var wasVisible = before.has(c);
        var willBeVisible = shownSet.has(c);
        if (wasVisible && !willBeVisible) toHide.push(c);
        else if (!wasVisible && willBeVisible) toShow.push(c);
        else if (wasVisible && willBeVisible) staying.push(c);
        else c.style.display = "none";
      });

      pills.forEach(function (p) { p.classList.toggle("active", p.getAttribute("data-filter") === state.format); });
      if (moreBtn) moreBtn.style.display = willShow.length > state.shown ? "" : "none";
      if (countEl) countEl.textContent = willShow.length + (willShow.length === 1 ? " project" : " projects");

      if (reduceMotion) {
        cards.forEach(function (c) { c.style.display = shownSet.has(c) ? "" : "none"; });
        return;
      }

      toHide.forEach(function (c) { var f = c.querySelector("iframe"); if (f) f.remove(); });

      grid.style.minHeight = gridH + "px";
      var clearMin = function () {
        if (myGen !== gen) return;
        grid.style.minHeight = "";
      };

      var gridRect = grid.getBoundingClientRect();
      toHide.concat(staying).forEach(function (c) {
        var snap = before.get(c);
        if (!snap) return;
        var r = snap.rect;
        c.style.position = "absolute";
        c.style.left = (r.left - gridRect.left) + "px";
        c.style.top = (r.top - gridRect.top) + "px";
        c.style.width = r.width + "px";
        c.style.height = r.height + "px";
        c.style.margin = "0";
        c.style.zIndex = "1";
        c.style.boxSizing = "border-box";
        var th = c.querySelector(".op-work-thumb");
        if (th) {
          th.style.height = snap.thumbH + "px";
          th.style.flex = "none";
          th.style.minHeight = "0";
          th.style.aspectRatio = "auto";
        }
      });

      var staggerS = toHide.length <= 1 ? 0 : Math.round((EXIT_TOTAL - OUT_DUR) / (toHide.length - 1));
      var totalExitMs = toHide.length === 0 ? 0 : (toHide.length - 1) * staggerS + OUT_DUR;

      toHide.forEach(function (c, i) {
        c.classList.remove("op-entering");
        setTimeout(function () {
          if (myGen !== gen) return;
          c.classList.add("op-leaving");
          setTimeout(function () {
            if (myGen !== gen) return;
            c.style.display = "none";
            c.classList.remove("op-leaving");
            c.style.position = ""; c.style.left = ""; c.style.top = "";
            c.style.width = ""; c.style.height = ""; c.style.margin = "";
            c.style.zIndex = ""; c.style.boxSizing = "";
            var th = c.querySelector(".op-work-thumb");
            if (th) { th.style.height = ""; th.style.flex = ""; th.style.minHeight = ""; th.style.aspectRatio = ""; }
          }, OUT_DUR);
        }, i * staggerS);
      });

      var flipStart = Math.ceil(totalExitMs / 2);
      setTimeout(function () {
        if (myGen !== gen) return;

        staying.forEach(function (c) {
          c.style.position = ""; c.style.left = ""; c.style.top = "";
          c.style.width = ""; c.style.height = ""; c.style.margin = "";
          c.style.zIndex = ""; c.style.boxSizing = "";
          var th = c.querySelector(".op-work-thumb");
          if (th) { th.style.height = ""; th.style.flex = ""; th.style.minHeight = ""; th.style.aspectRatio = ""; }
        });

        toShow.forEach(function (c) {
          c.style.display = "";
          c.style.opacity = "0";
          c.style.pointerEvents = "none";
          c.style.transform = "scale(0.88)";
        });

        void grid.offsetWidth;

        var after = new Map();
        staying.forEach(function (c) {
          var newRect = c.getBoundingClientRect();
          var th = c.querySelector(".op-work-thumb");
          after.set(c, { rect: newRect, thumbH: th ? th.getBoundingClientRect().height : 0 });
        });

        staying.forEach(function (c) {
          var snap = before.get(c);
          var aft = after.get(c);
          if (!snap || !aft) return;
          var dx = snap.rect.left - aft.rect.left;
          var dy = snap.rect.top - aft.rect.top;
          var hasMove = Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5;
          var hasCardW = Math.abs(snap.rect.width - aft.rect.width) > 0.5;
          var hasCardH = Math.abs(snap.rect.height - aft.rect.height) > 0.5;
          var th = c.querySelector(".op-work-thumb");
          var hasThumb = th && Math.abs(snap.thumbH - aft.thumbH) > 0.5;
          if (hasMove) {
            c.style.transform = "translate(" + dx + "px, " + dy + "px)";
            c.style.transition = "none";
          }
          if (hasCardW) {
            c.style.width = snap.rect.width + "px";
            c.style.boxSizing = "border-box";
            c.style.transition = "none";
          }
          if (hasCardH) {
            c.style.height = snap.rect.height + "px";
            c.style.overflow = "hidden";
            c.style.boxSizing = "border-box";
            c.style.transition = "none";
          }
          if (hasThumb) {
            th.style.height = snap.thumbH + "px";
            th.style.flex = "none";
            th.style.minHeight = "0";
            th.style.transition = "none";
          }
          if (th) th.style.aspectRatio = "auto";
        });

        void grid.offsetWidth;

        var easing = SLIDE_DUR + "ms cubic-bezier(0.16, 1, 0.3, 1)";
        staying.forEach(function (c) {
          var snap = before.get(c);
          var aft = after.get(c);
          if (!snap || !aft) return;
          var dx = snap.rect.left - aft.rect.left;
          var dy = snap.rect.top - aft.rect.top;
          var hasMove = Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5;
          var hasCardW = Math.abs(snap.rect.width - aft.rect.width) > 0.5;
          var hasCardH = Math.abs(snap.rect.height - aft.rect.height) > 0.5;
          var th = c.querySelector(".op-work-thumb");
          var hasThumb = th && Math.abs(snap.thumbH - aft.thumbH) > 0.5;
          var ts = [];
          if (hasMove) ts.push("transform " + easing);
          if (hasCardW) ts.push("width " + easing);
          if (hasCardH) ts.push("height " + easing);
          if (ts.length) {
            c.style.transition = ts.join(", ");
            if (hasMove) c.style.transform = "";
            if (hasCardW) c.style.width = aft.rect.width + "px";
            if (hasCardH) c.style.height = aft.rect.height + "px";
          }
          if (hasThumb) {
            th.style.transition = "height " + easing;
            th.style.height = aft.thumbH + "px";
          }
          if (hasMove || hasCardW || hasCardH || hasThumb || !!th) {
            setTimeout(function () {
              if (myGen !== gen) return;
              c.style.transform = ""; c.style.transition = "";
              c.style.width = ""; c.style.height = ""; c.style.overflow = ""; c.style.boxSizing = "";
              if (th) { th.style.transition = ""; th.style.height = ""; th.style.flex = ""; th.style.minHeight = ""; th.style.aspectRatio = ""; }
            }, SLIDE_DUR);
          }
        });

        toShow.forEach(function (c, i) {
          setTimeout(function () {
            if (myGen !== gen) return;
            popIn(c);
          }, i * STAGGER_IN);
        });

        setTimeout(clearMin, toShow.length * STAGGER_IN + 600);
      }, flipStart);
    }

    pills.forEach(function (p) {
      p.addEventListener("click", function () {
        if (p.classList.contains("active")) return;
        state.format = p.getAttribute("data-filter");
        state.shown = PAGE;
        render();
      });
    });
    if (nicheSelect) nicheSelect.addEventListener("change", function () { state.niche = nicheSelect.value; state.shown = PAGE; render(); });
    if (moreBtn) moreBtn.addEventListener("click", function () { state.shown += PAGE; render(); });
    render();
  }

  function init() { reveal(); workGrid(); }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();

/* =====================================================================
   SERVICES PAGE — process section spring animation
   ===================================================================== */
(function () {
  "use strict";

  var spring = { val: 0, vel: 0, target: 0, rafId: null, lastTime: null };
  var SK = 70, SD = 22, SM = 0.7;

  function stepSpring(dt) {
    var force = -SK * (spring.val - spring.target) - SD * spring.vel;
    spring.vel += (force / SM) * dt;
    spring.val += spring.vel * dt;
    spring.val = Math.max(0, Math.min(1, spring.val));
  }

  function getScrollProgress(el) {
    var r = el.getBoundingClientRect(), vh = window.innerHeight;
    return Math.max(0, Math.min(1, (-r.top + vh * 0.8) / (r.height + vh * 0.3)));
  }

  function transformN(sp) {
    return sp <= 0.7 ? sp * (0.8 / 0.7) : 0.8 + (sp - 0.7) / 0.3 * 0.2;
  }

  function initPaths() {
    var path = document.querySelector(".proc-path-main");
    var proc = document.querySelector(".proc");
    if (!path || !proc) return;
    var len = path.getTotalLength ? path.getTotalLength() : 1800;
    path.style.strokeDasharray = len;
    path.style.strokeDashoffset = len;

    function tick(now) {
      if (spring.lastTime === null) spring.lastTime = now;
      var dt = Math.min((now - spring.lastTime) / 1000, 0.05);
      spring.lastTime = now;
      stepSpring(dt);
      path.style.strokeDashoffset = len * (1 - spring.val);
      var settled = Math.abs(spring.val - spring.target) < 0.0005 && Math.abs(spring.vel) < 0.0005;
      spring.rafId = settled ? null : requestAnimationFrame(tick);
    }

    function onScroll() {
      spring.target = transformN(getScrollProgress(proc));
      if (spring.rafId === null) { spring.lastTime = null; spring.rafId = requestAnimationFrame(tick); }
    }

    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  }

  function initDesktop() {
    var cards = Array.prototype.slice.call(document.querySelectorAll(".proc-desktop .proc-card"));
    if (!cards.length) return;
    var thresholds = [0, 0.28, 0.55, 0.80];
    cards[0].classList.add("visible");
    if ("IntersectionObserver" in window) {
      var io = new IntersectionObserver(function (entries) {
        if (entries[0].isIntersecting) {
          cards.forEach(function (c, i) { if (i === 0) return; setTimeout(function () { c.classList.add("visible"); }, i * 200); });
          io.disconnect();
        }
      }, { threshold: 0.05 });
      var proc = document.querySelector(".proc");
      if (proc) io.observe(proc);
    }
    function updateActive() {
      cards.forEach(function (c, i) { c.classList.toggle("active", spring.val >= thresholds[i]); });
      requestAnimationFrame(updateActive);
    }
    requestAnimationFrame(updateActive);
  }

  function initMobile() {
    var mCards = Array.prototype.slice.call(document.querySelectorAll(".proc-mcard"));
    var vfill = document.querySelector(".proc-vfill");
    if (!mCards.length) return;
    var thresholds = [0, 0.28, 0.55, 0.80];
    if ("IntersectionObserver" in window) {
      var mio = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("visible"); mio.unobserve(e.target); } });
      }, { threshold: 0.15 });
      mCards.forEach(function (c) { mio.observe(c); });
    } else {
      mCards.forEach(function (c) { c.classList.add("visible"); });
    }
    function onScroll() {
      var proc = document.querySelector(".proc");
      if (!proc) return;
      var progress = getScrollProgress(proc);
      mCards.forEach(function (c, i) { c.classList.toggle("active", spring.val >= thresholds[i]); });
      if (vfill) vfill.style.clipPath = "inset(0 0 " + Math.max(0, (1 - progress) * 100).toFixed(1) + "% 0)";
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  }

  function initProcCta() {
    var btn = document.querySelector(".proc-cta-btn");
    if (!btn) return;
    var sweep = btn.querySelector(".proc-cta-sweep");
    if (!sweep) return;
    btn.addEventListener("mouseenter", function () {
      sweep.style.transition = "transform 0.8s ease-out";
      sweep.style.transform = "skewX(-20deg) translateX(320%)";
    });
    btn.addEventListener("mouseleave", function () {
      sweep.style.transition = "none";
      sweep.style.transform = "skewX(-20deg) translateX(0%)";
      setTimeout(function () { sweep.style.transition = ""; }, 50);
    });
  }

  function init() { initPaths(); initDesktop(); initMobile(); initProcCta(); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();

/* =====================================================================
   WORK PAGE — inline video, format sync, niche dropdown
   ===================================================================== */
(function () {
  "use strict";

  document.addEventListener("click", function (e) {
    var work = e.target.closest("[data-video]");
    if (!work) return;
    if (e.target.tagName === "IFRAME") return;
    e.stopPropagation();
    e.preventDefault();
    var id = work.getAttribute("data-video");
    var thumb = work.querySelector(".op-work-thumb");
    if (!thumb) return;
    if (thumb.querySelector("iframe")) return;
    document.querySelectorAll("[data-work-grid] .op-work-thumb iframe").forEach(function (f) { f.remove(); });
    var iframe = document.createElement("iframe");
    iframe.src = "https://www.youtube-nocookie.com/embed/" + id + "?autoplay=1&rel=0&modestbranding=1";
    iframe.title = "Video player";
    iframe.setAttribute("allow", "autoplay; encrypted-media; fullscreen; picture-in-picture");
    iframe.setAttribute("allowfullscreen", "");
    iframe.style.cssText = "position:absolute;inset:0;width:100%;height:100%;border:0;border-radius:inherit;z-index:2;opacity:0;transition:opacity 0.35s ease;";
    iframe.addEventListener("load", function () { iframe.style.opacity = "1"; });
    thumb.appendChild(iframe);
  }, true);

  function syncFormat() {
    var grid = document.querySelector("[data-work-grid]");
    var pills = document.querySelectorAll("[data-filter]");
    if (!grid || !pills.length) return;
    function update() {
      var active = document.querySelector("[data-filter].active");
      grid.setAttribute("data-current-format", active ? active.getAttribute("data-filter") : "all");
    }
    var mo = new MutationObserver(update);
    pills.forEach(function (p) { mo.observe(p, { attributes: true, attributeFilter: ["class"] }); });
    update();
  }

  function initNicheDropdown() {
    var wrap = document.getElementById("wkNicheDropdown");
    if (!wrap) return;
    var btn = wrap.querySelector(".wk-cselect-btn");
    var panel = wrap.querySelector(".wk-cselect-panel");
    var label = wrap.querySelector(".wk-cselect-label");
    var opts = wrap.querySelectorAll(".wk-cselect-opt");
    var hiddenSelect = wrap.querySelector("[data-niche-select]");

    function open()  { panel.classList.add("open");    btn.setAttribute("aria-expanded", "true"); }
    function close() { panel.classList.remove("open"); btn.setAttribute("aria-expanded", "false"); }
    function select(val, text) {
      label.textContent = text;
      opts.forEach(function (o) {
        var active = o.getAttribute("data-niche-val") === val;
        o.classList.toggle("active", active);
        o.setAttribute("aria-selected", active ? "true" : "false");
      });
      hiddenSelect.value = val;
      hiddenSelect.dispatchEvent(new Event("change"));
      close();
    }

    btn.addEventListener("click", function (e) {
      e.stopPropagation();
      panel.classList.contains("open") ? close() : open();
    });
    opts.forEach(function (o) {
      o.addEventListener("click", function () { select(o.getAttribute("data-niche-val"), o.textContent.trim()); });
    });
    document.addEventListener("click", function (e) { if (!wrap.contains(e.target)) close(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") close(); });
  }

  function init() { syncFormat(); initNicheDropdown(); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();

/* =====================================================================
   PRICING PAGE — electric arc borders + FAQ accordion
   ===================================================================== */
function startElectricBorders() {
  function hash(e) { return 43758.5453 * Math.sin(12.9898 * e) % 1; }
  function noise(e, t) {
    var a = Math.floor(e), i = Math.floor(t), r = e - a, s = t - i,
        n = hash(a + 57 * i), l = hash(a + 1 + 57 * i),
        o = hash(a + (i + 1) * 57), d = hash(a + 1 + (i + 1) * 57),
        c = r * r * (3 - 2 * r), m = s * s * (3 - 2 * s);
    return n * (1 - c) * (1 - m) + l * c * (1 - m) + o * (1 - c) * m + d * c * m;
  }
  function fbm(e, octaves, lacunarity, gain, amp, freq, time, chan, firstScale) {
    var d = 0, c = amp, m = freq;
    for (var r = 0; r < octaves; r++) {
      var t = c; if (r === 0) t *= firstScale;
      d += t * noise(m * e + 100 * chan, time * m * 0.3);
      m *= lacunarity; c *= gain;
    }
    return d;
  }
  function arcPt(cx, cy, radius, startAngle, sweep, t) {
    var n = startAngle + sweep * t;
    return { x: cx + radius * Math.cos(n), y: cy + radius * Math.sin(n) };
  }
  function roundRectPt(t, x, y, w, h, r) {
    var n = h - 2 * r, l = w - 2 * r, o = Math.PI * r / 2,
        total = t * (2 * n + 2 * l + 4 * o), c = 0;
    if (total <= (c) + l)       return { x: x + r + (total - c) / l * l, y: y };
    if (total <= (c += l) + o)  return arcPt(x + w - r, y + r, r, -Math.PI / 2, Math.PI / 2, (total - c) / o);
    if (total <= (c += o) + n)  return { x: x + w, y: y + r + (total - c) / n * n };
    if (total <= (c += n) + o)  return arcPt(x + w - r, y + h - r, r, 0, Math.PI / 2, (total - c) / o);
    if (total <= (c += o) + l)  return { x: x + w - r - (total - c) / l * l, y: y + h };
    if (total <= (c += l) + o)  return arcPt(x + r, y + h - r, r, Math.PI / 2, Math.PI / 2, (total - c) / o);
    if (total <= (c += o) + n)  return { x: x, y: y + h - r - (total - c) / n * n };
    return arcPt(x + r, y + r, r, Math.PI, Math.PI / 2, (total - (c += n)) / o);
  }

  window.reinitFaqAccordion = function reinitFaqAccordion() {
    var faq = document.getElementById('faq');
    if (!faq) return;
    var accentMap = {
      'What types of videos do you edit?':                  '#f472b6',
      "What's your typical turnaround time?":               '#a855f7',
      'How do I send you my footage?':                      '#3b82f6',
      'Can I request changes after delivery?':              '#22d3ee',
      'Do you offer subscriptions or bulk packages?':       '#10b981',
      'Who owns the final files?':                          '#eab308',
      'Can you repurpose my long-form content into shorts?':'#f97316',
      'Do you match my existing editing style?':            '#f43f5e'
    };
    function accent(btn) {
      var s = btn.querySelector('span');
      return s ? (accentMap[s.textContent.trim()] || '#a855f7') : '#a855f7';
    }
    function openItem(btn, panel, ac) {
      panel.classList.remove('grid-rows-[0fr]', 'opacity-0');
      panel.classList.add('grid-rows-[1fr]', 'opacity-100');
      btn.style.background = 'linear-gradient(155deg,' + ac + '14 0%,#0f0b0a 70%)';
      btn.style.borderColor = ac + '80';
      btn.style.boxShadow = '0 0 0 1px ' + ac + '33,0 20px 50px -16px ' + ac + '66';
      var icon = btn.querySelector('div');
      if (icon) { icon.style.background = ac + '26'; icon.style.color = ac; }
    }
    function closeItem(btn, panel) {
      panel.classList.add('grid-rows-[0fr]', 'opacity-0');
      panel.classList.remove('grid-rows-[1fr]', 'opacity-100');
      btn.style.background = '#0f0b0a';
      btn.style.borderColor = 'rgba(255,255,255,0.10)';
      btn.style.boxShadow = 'none';
      var icon = btn.querySelector('div');
      if (icon) { icon.style.background = 'rgba(255,255,255,0.05)'; icon.style.color = 'rgba(255,255,255,0.55)'; }
    }
    faq.querySelectorAll('button.group').forEach(function (btn) {
      var panel = btn.nextElementSibling;
      if (!panel || !panel.classList.contains('grid')) return;
      var ac = accent(btn);
      btn.addEventListener('click', function () {
        var isOpen = panel.classList.contains('grid-rows-[1fr]');
        var section = btn.closest('.lg\\:hidden') || btn.closest('.hidden');
        var scope = section || faq;
        scope.querySelectorAll('button.group').forEach(function (b) {
          var p = b.nextElementSibling;
          if (p && p.classList.contains('grid')) closeItem(b, p);
        });
        if (!isOpen) openItem(btn, panel, ac);
      });
      btn.addEventListener('mouseenter', function () {
        if (!panel.classList.contains('grid-rows-[1fr]')) {
          btn.style.borderColor = ac + '5c';
          btn.style.boxShadow = '0 0 0 1px ' + ac + '1f,0 10px 30px -12px ' + ac + '40';
        }
      });
      btn.addEventListener('mouseleave', function () {
        if (!panel.classList.contains('grid-rows-[1fr]')) {
          btn.style.borderColor = 'rgba(255,255,255,0.10)';
          btn.style.boxShadow = 'none';
        }
      });
    });
  };

  document.querySelectorAll('[style*="--electric-border-color"]').forEach(function (container) {
    var canvas = container.querySelector('canvas');
    if (!canvas) return;
    var colorMatch = container.getAttribute('style').match(/--electric-border-color:\s*([^;]+)/);
    var color = colorMatch ? colorMatch[1].trim() : '#3b82f6';
    var speed = 1, chaos = 0.12, borderRadius = 24;
    var ctx = canvas.getContext('2d');
    if (!ctx) return;
    var progress = 0, lastTime = 0, rafId;

    function resize() {
      var rect = container.getBoundingClientRect();
      var pw = rect.width + 120, ph = rect.height + 120;
      var dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = pw * dpr; canvas.height = ph * dpr;
      canvas.style.width = pw + 'px'; canvas.style.height = ph + 'px';
      ctx.scale(dpr, dpr);
      return { width: pw, height: ph };
    }

    var size = resize();
    var W = size.width, H = size.height;

    function draw(ts) {
      var dt = (ts - lastTime) / 1000;
      progress += dt * speed; lastTime = ts;
      var dpr = Math.min(window.devicePixelRatio || 1, 2);
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.scale(dpr, dpr);
      ctx.strokeStyle = color; ctx.lineWidth = 1; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
      var cw = W - 120, ch = H - 120;
      var r = Math.min(borderRadius, Math.min(cw, ch) / 2);
      var steps = Math.floor((2 * (cw + ch) + 2 * Math.PI * r) / 2);
      ctx.beginPath();
      for (var e = 0; e <= steps; e++) {
        var tVal = e / steps;
        var pt = roundRectPt(tVal, 60, 60, cw, ch, r);
        var nx = fbm(8 * tVal, 10, 1.6, 0.7, chaos, 10, progress, 0, 0);
        var ny = fbm(8 * tVal, 10, 1.6, 0.7, chaos, 10, progress, 1, 0);
        var lx = pt.x + 60 * nx, ly = pt.y + 60 * ny;
        if (e === 0) ctx.moveTo(lx, ly); else ctx.lineTo(lx, ly);
      }
      ctx.closePath(); ctx.stroke();
      rafId = requestAnimationFrame(draw);
    }

    var ro = new ResizeObserver(function () { var s = resize(); W = s.width; H = s.height; });
    ro.observe(container);
    rafId = requestAnimationFrame(draw);
  });
}

document.addEventListener('DOMContentLoaded', function () {
  startElectricBorders();
  if (typeof reinitFaqAccordion === 'function') reinitFaqAccordion();
});

// Replace Orwyx O logo placeholders with the real logo file
(function () {
  var LOGO = '/assets/orwyx%20o%20logo.png';
  function needsReplace(src) {
    return src && (src.indexOf('iridescent-a') !== -1 || src.indexOf('a-gradient-288') !== -1);
  }

  // Intercept img.src setter so React re-renders can't undo our patch
  var imgDesc = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'src');
  Object.defineProperty(HTMLImageElement.prototype, 'src', {
    get: imgDesc.get,
    set: function (val) {
      imgDesc.set.call(this, needsReplace(val) ? LOGO : val);
    },
    configurable: true,
  });

  // Intercept source.srcset setter
  var srcsetDesc = Object.getOwnPropertyDescriptor(HTMLSourceElement.prototype, 'srcset');
  if (srcsetDesc && srcsetDesc.set) {
    Object.defineProperty(HTMLSourceElement.prototype, 'srcset', {
      get: srcsetDesc.get,
      set: function (val) {
        srcsetDesc.set.call(this, needsReplace(val) ? LOGO : val);
      },
      configurable: true,
    });
  }

  // Also patch any already-present images on initial load
  function patchAll() {
    document.querySelectorAll('img').forEach(function (img) {
      if (needsReplace(img.getAttribute('src'))) img.src = LOGO;
    });
    document.querySelectorAll('source').forEach(function (s) {
      if (needsReplace(s.getAttribute('srcset'))) s.srcset = LOGO;
    });
  }
  patchAll();
  var mo = new MutationObserver(patchAll);
  mo.observe(document.body, { childList: true, subtree: true });
  window._logoObs = mo;
}());
