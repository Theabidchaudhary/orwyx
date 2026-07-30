/* Orwyx runtime content layer.
   Post-migration: the amphora bundle's source HTML is now natively English (no more
   runtime German->English translation). This file keeps only the NON-language
   functionality that made the client-side companion-page system work: locating each
   section's DOM root, hiding sections not relevant to the current route, injecting
   route-specific copy overrides (unchanged from the original override text — only the
   FIND keys were re-keyed from German source fragments to their now-baked-in English
   equivalents), swapping images/logos, fixing links, and the legal page injection for
   /terms/ and /privacy/. */
(function() {
  "use strict";

  // =====================================
  // SECTION DEFINITIONS
  // =====================================
  // Each section's DOM root is located by finding the first section/header/nav/footer
  // element whose text includes `sig` (a short, unique fragment now present verbatim in
  // the migrated English markup). `root` is used instead for nav/footer, which have a
  // reliable direct selector.
  var SECTIONS = [{
      key: "nav",
      sig: null,
      root: "nav"
    },
    {
      key: "hero",
      sig: "Consider it"
    },
    {
      key: "heart",
      sig: "A direct line to your editor —"
    },
    {
      key: "marquee",
      sig: "Industries we've cut great things for"
    },
    {
      key: "process",
      sig: "in 4 steps"
    },
    {
      key: "system",
      sig: "proven system"
    },
    {
      key: "outcomes",
      sig: "What should your"
    },
    {
      key: "stats",
      sig: "Projects successfully delivered"
    },
    {
      key: "feast",
      sig: "feast for the eyes"
    },
    {
      key: "impact",
      sig: "… with real"
    },
    {
      key: "pricing",
      sig: "Where's the"
    },
    {
      key: "quality",
      sig: "Quality promises"
    },
    {
      key: "comparison",
      sig: "DIY, agency"
    },
    {
      key: "testimonials",
      sig: "clients are saying"
    },
    {
      key: "contact",
      sig: "Ready for your"
    },
    {
      key: "faq",
      sig: "Frequently asked"
    },
    {
      key: "footer",
      sig: null,
      root: "footer"
    },
  ];

  // =====================================
  // IMAGE / LOGO / BRAND DATA
  // =====================================
  var IMAGES = {
    "grechows-beauty-hero": "https://i.ytimg.com/vi/imsud0eJlcM/hqdefault.jpg",
    "evltn-gym-hero": "https://i.ytimg.com/vi/38yHi3Fyywg/hqdefault.jpg",
    "nord-ostsee-hero": "https://i.ytimg.com/vi/yY2kwfpGHgg/hqdefault.jpg",
    "cords-cafe-hero": "https://i.ytimg.com/vi/5bSUQcSx6PU/hqdefault.jpg"
  };
  var AVATAR_IMGS = ["salam", "marcel"];
  var MARQUEE_NICHES = {
    "cords-logo": "YouTube",
    "evltn-gym": "fitness",
    "grechows-beauty": "Beauty",
    "nol": "REAL ESTATE",
    "remco": "SaaS",
    "therapie-mueller": "Healthcare",
    "wia": "Agencies",
    "bremer-stadtmusikanten": "Music",
    "uni-bremen": "EDUCATION",
    "clickless": "E-commerce",
    "life-lounge-bar": "Hospitality",
    "oxzy": "STARTUPS",
    "antik-vehrs": "AUTOMOTIVE",
    "clockwise-wordmark": "FINANCE",
    "partner-logos": "Brands"
  };

  // Per-word style map — each industry word gets its own font/weight/case/style
  // so the marquee reads like a row of different brand logos, not one repeated font.
  // Fonts are loaded via Google Fonts <link> in index.html <head>.
  // Each entry: [fontFamily, fontWeight, textTransform, fontStyle, letterSpacing, extraClasses]
  var MARQUEE_STYLES = {
    "YouTube":        ["'Montserrat', sans-serif",         "800", "none",      "normal", "-0.02em", ""],
    "fitness":        ["'Archivo', sans-serif",            "800", "lowercase", "normal", "-0.03em", ""],
    "Beauty":         ["'Cormorant Garamond', serif",      "600", "none",      "italic", "0.01em",  ""],
    "REAL ESTATE":    ["'Oswald', sans-serif",             "500", "uppercase", "normal", "0.12em",  ""],
    "SaaS":           ["'Space Grotesk', sans-serif",      "600", "none",      "normal", "-0.02em", ""],
    "Healthcare":     ["'Lora', serif",                    "600", "none",      "italic", "0",       ""],
    "Agencies":       ["'Work Sans', sans-serif",          "700", "none",      "normal", "-0.01em", ""],
    "Music":          ["'Pacifico', cursive",              "400", "none",      "normal", "0",       ""],
    "EDUCATION":      ["'Bebas Neue', sans-serif",         "400", "uppercase", "normal", "0.04em",  ""],
    "E-commerce":     ["'DM Serif Display', serif",        "400", "none",      "normal", "-0.01em", ""],
    "Hospitality":    ["'Playfair Display', serif",        "700", "none",      "italic", "-0.01em", ""],
    "STARTUPS":       ["'Montserrat', sans-serif",         "900", "uppercase", "normal", "0.08em",  ""],
    "AUTOMOTIVE":     ["'Archivo Black', sans-serif",      "900", "uppercase", "normal", "0.02em",  ""],
    "FINANCE":        ["'IBM Plex Mono', monospace",       "500", "uppercase", "normal", "0.06em",  ""],
    "Brands":         ["'Caveat', cursive",                "700", "none",      "normal", "0",       ""]
  };
  var BRAND = {
    "name": "Orwyx",
    "tagline": "Post-production that moves people.",
    "description": "Orwyx is a premium video editing agency for creators, startups, and brands — 1,400+ videos delivered across 40+ niches, in as fast as 48 hours.",
    "url": "https://orwyx.com/",
    "email": "Theabidchaudhary@gmail.com",
    // Same phone number in the two formats the page needs: a spaced-out version for
    // anything a visitor reads, and a digits-only version for "tel:" links.
    "phone": "+44 741 308 9165",
    "phoneDigits": "+447413089165",
    "whatsapp": "https://wa.me/447413089165",
    "availability": "Currently booking — 2026",
    "fiverr": "https://www.fiverr.com/theabidchaudhry",
    "youtube": "https://www.youtube.com/@pixxelpulse",
    "instagram": "https://www.instagram.com/pixxelpulse",
    "linkedin": "https://www.linkedin.com/company/pixxelpulse",
    "showreelId": "H6Ax9xV9BE4"
  };

  // =====================================
  // ROUTE OVERRIDES
  // =====================================
  // Companion-page routing: on these routes, sections not in the keep-list get hidden
  // (display:none on their real section root — never removed/reordered) and the sections
  // that stay get this route's own content instead of the home page's.
  var ROUTE = location.pathname;

  // PAGE_KEEP: which sections stay visible on each companion route (everything else on
  // that route gets hidden, not removed).
  var PAGE_KEEP = {
    "/contact/": ["hero", "contact", "faq"],
    "/pricing/": ["hero", "pricing", "faq"],
    "/services/": ["hero", "outcomes", "quality", "process"],
    "/about/": ["hero", "comparison", "testimonials", "contact"],
    "/terms/": ["hero"],
    "/privacy/": ["hero"]
  };

  // ROUTE_HERO: route-specific copy overrides for the hero section, re-keyed to the
  // now-baked-in English source fragments (originally these matched the pre-migration
  // German text; override VALUES are unchanged from before). Each map is applied only to
  // its named section's root, only on the given route, via an exact text-node match.
  var ROUTE_HERO = {
    "/contact/": {
      "Professional": "Got a project",
      "short-form": "in mind",
      "YouTube": "let's talk.",
      "& brand videos": "",
      "— 1,400+ delivered across 40+ niches.": "Tell us about your project — we reply within 12 hours.",
      "First cut in just 48–72 hours": "Reply within 12h",
      "clear scope, no strings attached": "no strings attached",
      "Get your free quote": "Get in touch"
    },
    "/pricing/": {
      "Professional": "Simple",
      "short-form": "models",
      "YouTube": "serious output.",
      "& brand videos": "",
      "— 1,400+ delivered across 40+ niches.": "Pick the engagement that matches how you publish. Every model ships with the same editors and the same 48–72h rhythm.",
      "First cut in just 48–72 hours": "Fixed scope",
      "clear scope, no strings attached": "fixed price",
      "Get your free quote": "Talk to us"
    },
    "/services/": {
      "Professional": "Everything after",
      "short-form": "the record",
      "YouTube": "button.",
      "& brand videos": "",
      "— 1,400+ delivered across 40+ niches.": "From a single hero video to a full monthly content engine — six disciplines run by one team, on one quality bar.",
      "First cut in just 48–72 hours": "48–72h turnaround",
      "clear scope, no strings attached": "every project",
      "Get your free quote": "Contact us"
    },
    "/about/": {
      "Professional": "Built in the",
      "short-form": "edit",
      "YouTube": "one frame at a time.",
      "& brand videos": "",
      "— 1,400+ delivered across 40+ niches.": "Orwyx started in December 2018 as one freelance editor taking on projects nobody else wanted to rush. Seven years and 1,300+ videos later, it's a full post-production team trusted by creators, startups, and brands across 40+ industries.",
      "First cut in just 48–72 hours": "Since 2018",
      "clear scope, no strings attached": "1,400+ videos delivered",
      "Get your free quote": "Start a project"
    },
    "/terms/": {
      "Professional": "Terms",
      "short-form": "of Service",
      "YouTube": "in plain English.",
      "& brand videos": "",
      "— 1,400+ delivered across 40+ niches.": "The terms that govern working with Orwyx.",
      "First cut in just 48–72 hours": "Plain English",
      "clear scope, no strings attached": "no fine print",
      "Get your free quote": "Contact us"
    },
    "/privacy/": {
      "Professional": "Privacy",
      "short-form": "Policy",
      "YouTube": "in plain English.",
      "& brand videos": "",
      "— 1,400+ delivered across 40+ niches.": "Orwyx (“we”, “us”) respects your privacy. This policy explains what we collect through this site and why.",
      "First cut in just 48–72 hours": "Plain English",
      "clear scope, no strings attached": "no fine print",
      "Get your free quote": "Contact us"
    },
  };

  // ROUTE_OVERRIDES: additional per-section, per-route copy overrides (beyond the hero),
  // keyed as ROUTE_OVERRIDES[route][sectionKey] = { oldText: newText, ... }.
  var ROUTE_OVERRIDES = {
    "/pricing/": {
      "pricing": {
        "One-off & campaigns — a scoped quote for a defined deliverable.": "A scoped quote for a defined deliverable — from a single hero video to a launch campaign pack.",
        "For creators & brands that publish every week.": "A dedicated editor pod and a standing production pipeline for creators and brands that publish weekly.",
        "Brand motion systems, intros & animation.": "White-label capacity under your brand: NDA-first, SLA-backed, invisible to your clients.",
        "Custom quote": "Custom · scoped quote",
        "From $1,000/mo": "From $1,000 / per month",
        "Full ownership, forever": "Full ownership of your masters",
        "Get a project quote": "Get a quote",
        "Short-form, long-form & motion mixed": "Standing 48–72h delivery windows",
        "Logo animation & brand stingers": "Dedicated editor pods under NDA",
        "Lower thirds & title packages": "Your-brand delivery",
        "Explainer & product animation": "Custom SLA turnaround",
        "Request motion quote": "Talk partnerships",
        "Dedicated editor pods under NDA": "Talk through your use case",
        "Your-brand delivery — we're invisible": "Get a same-day recommendation",
        "Custom SLA turnaround": "No pressure, no obligation",
        "Elastic surge capacity": "20-minute call, no cost",
        "Talk partnerships": "Book a free call",
      },
    },
    "/about/": {
      "heart": {
        "Built with": "",
        "heart": "",
        "for your content": "From freelancer to agency — without losing the obsession.",
        "Our long-standing quality standards have proven themselves time and again.": "",
        "Personal &": "From freelancer",
        "direct": "to agency",
        "A direct line to your editor — no ticket queues or account managers in between.": "Most agencies start with a pitch deck. Ours started with deadlines: years of marketplace work where the only marketing was the last delivery. That history shaped everything — the turnaround discipline, the communication habits, and the belief that the work itself is the sales team.",
        "More": "Editor-led,",
        "than just pretty": "not pitch-deck-led",
        "Cut to hold attention and convert viewers — not just to look good.": "Today Orwyx runs as an editor-led studio: every project has a dedicated editor, every edit gets a second pair of eyes, and every client gets the same 48–72 hour pulse that built our reputation.",
        "For channels": "",
        "that want to": "",
        "grow": "",
        "For creators, startups, brands and B2B — 1,400+ videos across 40+ niches.": "",
      },
      "quality": {
        "Satisfaction": "Craft ",
        "guarantee": "over volume",
        "We only deliver once the result truly convinces you. So you take no risk at all.": "We'd rather ship one edit that gets rewatched than ten that get skipped. Every frame earns its place — that bar doesn't move with deadline pressure.",
        "Unlimited": "Speed ",
        "revisions": "is a system",
        "We refine the cut until it truly fits. Design tweaks are included, at no extra cost.": "48–72 hour delivery isn't heroics, it's process: structured intake, dedicated editors, and a feedback loop designed to converge, not circle.",
        "Fixed price": "Partners, ",
        "from day one": "not vendors",
        "One quote before we start. Scope changes get a heads-up first — no surprise invoices.": "We learn your audience, your voice, and your numbers. The tenth video should be meaningfully better than the first — that only happens in a real partnership.",
      },
    },
  };

  // =====================================
  // LEGAL PAGES
  // =====================================
  // Full HTML for the /terms/ and /privacy/ pages, injected as a plain sibling next to
  // the (kept, retexted) hero section on those two routes only.
  // Shared inline styles for the legal-page prose below, pulled out once instead of
  // being retyped on every heading/paragraph.
  var LEGAL_WRAP_STYLE = "max-width:42rem;margin:0 auto;padding:2rem 1.5rem 6rem";
  var LEGAL_H2_STYLE =
    "font-family:'Poppins',sans-serif;font-weight:700;font-size:1.15rem;margin-top:2.25rem;color:#f4f4f5";
  var LEGAL_P_STYLE = "margin-top:.6rem;line-height:1.8;color:#a1a1aa;font-size:.98rem";
  var LEGAL_FOOTNOTE_STYLE = "margin-top:2.5rem;font-size:.8rem;color:#71717a";

  // Builds one "<h2>heading</h2>" block for a legal-page section.
  function legalHeading(text) {
    return "<h2 style=\"" + LEGAL_H2_STYLE + "\">" + text + "</h2>";
  }

  // Builds one "<p>paragraph</p>" block for a legal-page section.
  function legalParagraph(html) {
    return "<p style=\"" + LEGAL_P_STYLE + "\">" + html + "</p>";
  }

  var ROUTE_LEGAL = {
    "/terms/":
      "<div style=\"" + LEGAL_WRAP_STYLE + "\">" +
      legalHeading("Scope of work") +
      legalParagraph(
        "Every project begins with a written scope: deliverables, timeline, and price. " +
        "Work outside the agreed scope is quoted separately before it starts — no surprise invoices."
      ) +
      legalHeading("Revisions") +
      legalParagraph(
        "Projects include the revision rounds stated in their scope. Revisions cover changes " +
        "to the agreed deliverable; new creative directions are scoped as new work."
      ) +
      legalHeading("Ownership") +
      legalParagraph(
        "Upon full payment, you own the final deliverables outright. We retain the right to " +
        "display completed work in our portfolio unless a confidentiality agreement says " +
        "otherwise — white-label partners are never credited or displayed."
      ) +
      legalHeading("Your materials") +
      legalParagraph(
        "You confirm you have the rights to all footage, music, and assets you provide. " +
        "Licensed assets we source for your project are licensed in your name or under " +
        "licenses that permit your intended use."
      ) +
      legalHeading("Questions") +
      // Sourced from BRAND.email (defined above) instead of a separate hardcoded copy,
      // so updating the contact email in BRAND updates this page too.
      legalParagraph("Anything unclear? Email " + BRAND.email + " before starting a project.") +
      "<p style=\"" + LEGAL_FOOTNOTE_STYLE + "\">Last updated: July 2026</p>" +
      "</div>",

    "/privacy/":
      "<div style=\"" + LEGAL_WRAP_STYLE + "\">" +
      legalHeading("What we collect") +
      legalParagraph(
        "When you contact us we collect the details you provide: your name, email address, " +
        "and project information. We use standard, privacy-respecting analytics to understand " +
        "how the site is used (pages visited, approximate region, device type) — never to " +
        "identify you personally."
      ) +
      legalHeading("How we use it") +
      legalParagraph(
        "Contact details are used solely to respond to your inquiry and manage projects you " +
        "hire us for. We do not sell, rent, or share your information with third parties for marketing."
      ) +
      legalHeading("Embedded video") +
      legalParagraph(
        "Portfolio videos are embedded from YouTube in privacy-enhanced mode " +
        "(youtube-nocookie.com). YouTube only sets cookies once you actively play a video, " +
        "and its own privacy policy applies from that point."
      ) +
      legalHeading("Your rights") +
      // Sourced from BRAND.email (defined above) instead of a separate hardcoded copy.
      legalParagraph(
        "You can request a copy or deletion of any personal data we hold about you at any " +
        "time by emailing " + BRAND.email + "."
      ) +
      "<p style=\"" + LEGAL_FOOTNOTE_STYLE + "\">Last updated: July 2026</p>" +
      "</div>"
  };

  // =====================================
  // CONTENT MAPPING
  // =====================================
  // Wire the route-specific override maps (above) onto the matching SECTIONS entries,
  // then pre-normalize their keys once so text-node matching stays cheap at runtime.
  var KEEP = PAGE_KEEP[ROUTE] || null;
  if (KEEP) {
    var heroOv = ROUTE_HERO[ROUTE];
    if (heroOv) SECTIONS.forEach(function(s) {
      if (s.key === "hero") s.routeMap = heroOv;
    });
    var ov = ROUTE_OVERRIDES[ROUTE];
    if (ov) SECTIONS.forEach(function(s) {
      if (ov[s.key]) s.routeMap = Object.assign({}, s.routeMap, ov[s.key]);
    });
  }

  function norm(s) {
    return (s || "").replace(/­/g, "").replace(/\s+/g, " ").trim();
  }
  SECTIONS.forEach(function(s) {
    if (s.routeMap) s.routeNMap = (function(map) {
      var o = {};
      for (var k in map) o[norm(k)] = map[k];
      return o;
    })(s.routeMap);
  });

  // =====================================
  // DOM HELPERS: SECTION LOOKUP + ROUTE OVERRIDES
  // =====================================
  var observer = null;

  // Applies a section's route-override map (if any) to its own text nodes only — an
  // exact, normalized text-node match, same mechanism the old translation layer used.
  function applyRouteOverrides(root, nmap) {
    if (!root || !nmap) return;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var n, changed = [];
    while ((n = walker.nextNode())) {
      var key = norm(n.nodeValue);
      if (key && Object.prototype.hasOwnProperty.call(nmap, key)) {
        var lead = (n.nodeValue.match(/^\s*/) || [""])[0],
          trail = (n.nodeValue.match(/\s*$/) || [""])[0];
        changed.push([n, lead + nmap[key] + trail]);
      }
    }
    changed.forEach(function(c) {
      c[0].nodeValue = c[1];
    });
  }

  function findContainer(sig) {
    // locate the first element whose text includes sig, climb to a section/main-child ancestor
    var all = document.querySelectorAll("section, main > div, footer, header, nav");
    for (var i = 0; i < all.length; i++) {
      if (all[i].textContent && all[i].textContent.indexOf(sig) >= 0) return all[i];
    }
    return null;
  }

  // Whole-section hide: display:none on the section's own real root. This never removes or
  // reorders a React-owned node (same safety rule as hideNode below), it just collapses the
  // layout space a section not needed on this page would otherwise reserve.
  function hideSection(el) {
    if (!el || el.getAttribute("data-orwyx-section-hidden")) return;
    el.setAttribute("data-orwyx-section-hidden", "1");
    el.style.display = "none";
  }

  // Cache each section's resolved root once found, so repeated runs (timed re-applies,
  // observer callbacks) don't re-scan the entire DOM every time — this was a major
  // source of main-thread cost on a page with many animated/lazy-mounting sections.
  function runSections() {
    SECTIONS.forEach(function(s) {
      var root = s._el && document.contains(s._el) ? s._el : null;
      if (!root) {
        if (s.root === "nav") root = document.querySelector("body > nav, header nav, nav");
        else if (s.root === "footer") root = document.querySelector("footer");
        else if (s.sig) root = findContainer(s.sig);
        s._el = root;
      }
      if (!root) return;
      if (KEEP && s.root !== "nav" && s.root !== "footer" && KEEP.indexOf(s.key) === -1) {
        hideSection(root);
        return;
      }
      if (s.routeNMap) applyRouteOverrides(root, s.routeNMap);
    });
  }

  // Terms/Privacy: append the legal prose as a new plain sibling right after the (kept,
  // retexted) hero section — a brand-new DOM node, never replacing/removing anything React
  // manages, so this is exactly as safe as the overlay() pattern used everywhere else here.
  function injectLegal() {
    var html = ROUTE_LEGAL[ROUTE];
    if (!html || document.querySelector("[data-orwyx-legal]")) return;
    var heroSection = SECTIONS.filter(function(s) {
      return s.key === "hero";
    })[0];
    var heroRoot = heroSection && heroSection._el;
    if (!heroRoot || !heroRoot.parentNode) return;
    var div = document.createElement("div");
    div.setAttribute("data-orwyx-legal", "1");
    div.innerHTML = html;
    heroRoot.parentNode.insertBefore(div, heroRoot.nextSibling);
  }

  // =====================================
  // BRAND GRADIENT + LOGO/AVATAR SWAPPING
  // =====================================
  // Amphora's own confirmed brand gradient (their primary CTA buttons + brand accents use
  // this exact linear-gradient(90deg, ...) with these exact hex values and stop percentages).
  var AURORA = "linear-gradient(90deg,#10c4e2 0%,#eb3a46 38%,#f28924 68%,#f49d07 100%)";
  var AURORA_SVG_STOPS = [
    ["0%", "#10c4e2"],
    ["38%", "#eb3a46"],
    ["68%", "#f28924"],
    ["100%", "#f49d07"]
  ];

  function gradientRingSvg(id) {
    var stops = AURORA_SVG_STOPS.map(function(s) {
      return '<stop offset="' + s[0] + '" stop-color="' + s[1] + '"></stop>';
    }).join("");
    return '<svg viewBox="0 0 32 32" fill="none" style="width:100%;height:100%" aria-hidden="true"><circle cx="16" cy="16" r="12.5" stroke="url(#' +
      id + ')" stroke-width="5.5"></circle><defs><linearGradient id="' + id +
      '" x1="5" y1="16" x2="28" y2="26" gradientUnits="userSpaceOnUse">' + stops + '</linearGradient></defs></svg>';
  }

  // IMPORTANT: never structurally replace/remove a DOM node React created (replaceWith,
  // el.remove(), source.remove()). React keeps its own reference to that exact node; if a
  // later re-render (scroll-linked animation, hover state, lazy mount) needs to update or
  // remove it, React calls removeChild/insertBefore against ITS remembered parent/node —
  // which throws ("not a child of this node") if we've already detached it ourselves. That
  // throw aborts React's commit and can leave large parts of the page permanently blank,
  // which is exactly the "page breaks after a certain section" bug this replaces.
  // Instead: hide the original node (opacity:0, kept in the DOM, in place) and append a new
  // overlay sibling on top of it. React never touches nodes it didn't create, so this is safe
  // no matter how many times the surrounding component re-renders.
  function hideNode(el) {
    if (!el || el.getAttribute("data-orwyx-hidden")) return;
    el.setAttribute("data-orwyx-hidden", "1");
    el.style.opacity = "0";
    el.style.pointerEvents = "none";
    el.setAttribute("aria-hidden", "true");
  }

  function overlay(parent, html, extraStyle) {
    if (!parent || parent.querySelector(":scope > [data-orwyx-ov]")) return null;
    var cs = getComputedStyle(parent);
    if (cs.position === "static") parent.style.position = "relative";
    var span = document.createElement("span");
    span.setAttribute("data-orwyx-ov", "");
    span.style.cssText =
      "position:absolute;inset:0;display:flex;align-items:center;pointer-events:none;" + (
        extraStyle || "");
    span.innerHTML = html;
    parent.appendChild(span);
    return span;
  }

  function wordmark(size, color) {
    return '<span class="orwyx-wm" style="font-weight:700;letter-spacing:-.02em;text-transform:lowercase;line-height:1;font-size:' +
      size + ';color:' + color + '"><span style="background:' + AURORA +
      ';-webkit-background-clip:text;background-clip:text;color:transparent">o</span>rwyx</span>';
  }

  function initialAvatarHtml() {
    return '<span style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-radius:9999px;color:#fff;font-weight:700;background:' +
      AURORA + '">A</span>';
  }

  // Inject a <style> tag with !important rules that enforce a uniform slot
  // size for every marquee item. CSS !important beats both Tailwind utility
  // classes AND inline styles, so React re-renders can't override it —
  // every word stays on the exact same baseline and the gap between any
  // two words (including the loop seam) stays identical.
  function injectMarqueeStyles() {
    if (document.getElementById("orwyx-marquee-styles")) return;
    var style = document.createElement("style");
    style.id = "orwyx-marquee-styles";
    style.textContent =
      ".orwyx-marquee-slot{" +
      // NO fixed width — let each word take its natural width so the VISUAL
      // gap between any two words is identical (just the CSS gap variable).
      // Fixed-width slots made short words like "SaaS" look isolated and
      // long words like "REAL ESTATE" look cramped.
      "flex-shrink:0 !important;" +
      "align-self:center !important;" +
      "display:flex !important;" +
      "align-items:center !important;" +
      "justify-content:center !important;" +
      // Keep a fixed HEIGHT so all words share the same baseline
      "height:2.5rem !important;" +
      "min-height:2.5rem !important;" +
      "max-height:2.5rem !important;" +
      // Kill any residual Framer Motion entrance-animation transforms
      "transform:none !important" +
      "}" +
      // The original <img> is hidden via opacity:0 but still takes up layout
      // space. display:none removes it from the flow entirely so only the
      // text span determines the slot's width — this makes the visual gap
      // between every pair of words exactly the CSS gap variable.
      ".orwyx-marquee-slot img{" +
      "display:none !important" +
      "}" +
      ".orwyx-marquee-slot [data-orwyx-ov]{" +
      "position:static !important;" +
      "inset:auto !important" +
      "}";
    document.head.appendChild(style);
  }

  function swapImages() {
    document.querySelectorAll("img").forEach(function(img) {
      if (img.getAttribute("data-orwyx-img-checked")) return;
      img.setAttribute("data-orwyx-img-checked", "1");
      var src = img.getAttribute("src") || "";
      // 1) marquee / partner client logos -> niche wordmark text (hide img, overlay text)
      //    Each word gets its own font/weight/case/style from MARQUEE_STYLES so the row
      //    reads like a strip of different brand logos — not one repeated Poppins.
      for (var m in MARQUEE_NICHES) {
        if (src.indexOf(m) >= 0) {
          var word = MARQUEE_NICHES[m];
          var st = MARQUEE_STYLES[word] || ["'Poppins',sans-serif","700","none","normal","-0.01em",""];
          hideNode(img);
          var parent = img.parentElement;
          // Tag the parent with a class so the injected CSS rule (see
          // injectMarqueeStyles) can enforce uniform height + kill transforms.
          // Width is NOT set — each word takes its natural width so the visual
          // gap between any two words is exactly the CSS gap variable.
          parent.classList.add("orwyx-marquee-slot");
          overlay(parent,
            '<span style="white-space:nowrap;font-family:' + st[0] +
            ';font-weight:' + st[1] +
            ';text-transform:' + st[2] +
            ';font-style:' + st[3] +
            ';letter-spacing:' + st[4] +
            ';color:#26262b;opacity:.78;font-size:1.35rem;' +
            'line-height:32px;display:inline-block;vertical-align:middle;text-align:center">' +
            word + '</span>');
          return;
        }
      }
      // 2) founder/consultant avatars -> real photo if alt="Abid Fareed", otherwise gradient initial
      for (var a = 0; a < AVATAR_IMGS.length; a++) {
        if (src.indexOf(AVATAR_IMGS[a]) >= 0) {
          var pic = img.closest("picture");
          hideNode(pic || img);
          if (img.alt && /abid/i.test(img.alt)) {
            var innerWrap = (pic || img).parentElement;
            var outerWrap = innerWrap && innerWrap.parentElement;
            if (outerWrap) outerWrap.style.padding = '2px';
            if (innerWrap) innerWrap.style.boxShadow = 'inset 0 0 0 2px rgba(13,13,13,0.85)';
            overlay(innerWrap,
              '<img src="/assets/abid-fareed-256.jpg" data-orwyx-img-checked="1" style="width:100%;height:100%;object-fit:cover;object-position:50% 50%;border-radius:0;" />',
              'overflow:hidden;border-radius:9999px;');
          } else {
            overlay((pic || img).parentElement, initialAvatarHtml());
          }
          return;
        }
      }
      // 3) case-study / content screenshots -> Orwyx work thumbnails (attribute-only, safe)
      for (var key in IMAGES) {
        if (src.indexOf(key) >= 0) {
          img.setAttribute("src", IMAGES[key]);
          // neutralize sibling <source> variants (attribute overwrite, never remove the node)
          var pic2 = img.closest("picture");
          if (pic2) {
            pic2.querySelectorAll("source").forEach(function(so) {
              so.setAttribute("srcset", IMAGES[key]);
            });
          }
          return;
        }
      }
    });
  }

  var orbitIconCounter = 0;

  function swapOrbitIcon() {
    // The "5 quality promises" orbit center: show just the gradient "o" letter at half
    // the size of the inner thin-border circle (w-28 = 7rem → 3.5rem).
    // The migrated HTML uses alt="Orwyx" (not "Amphora"), so also target by src pattern.
    document.querySelectorAll('img[alt="Amphora"], img[src*="a-gradient"]').forEach(function(img) {
      if (img.getAttribute("data-orwyx-orbit-checked")) return;
      img.setAttribute("data-orwyx-orbit-checked", "1");
      var pic = img.closest("picture");
      var target = pic || img;
      hideNode(target);
      var parent = target.parentElement;
      // Remove any wordmark overlay swapBrandLogos may have already placed (we own that node)
      var existing = parent.querySelector(":scope > [data-orwyx-ov]");
      if (existing) existing.remove();
      overlay(parent,
        '<span style="font-weight:700;line-height:1;font-size:5.46rem;transform:translateY(-0.06em);display:inline-block;' +
        'background:' + AURORA + ';-webkit-background-clip:text;background-clip:text;color:transparent">o</span>',
        "justify-content:center;align-items:center");
    });
  }

  function swapBrandLogos() {
    document.querySelectorAll(
      'img[src*="amphora"],img[alt="Amphora Logo"],img[alt="Amphora Full Logo"],img[alt*="Orwyx"]'
      ).forEach(function(img) {
      if (img.getAttribute("data-orwyx-brand-checked")) return;
      img.setAttribute("data-orwyx-brand-checked", "1");
      var src = (img.getAttribute("src") || "").toLowerCase();
      // Skip the orbit center O logo — it should stay as-is
      if (src.indexOf("orwyx%20o%20logo") >= 0 || src.indexOf("orwyx o logo") >= 0) return;
      var pic = img.closest("picture");
      var big = src.indexOf("word logo") >= 0 || (img.naturalWidth > 400) || src.indexOf(
        "transparent") >= 0;
      var target = pic || img;
      hideNode(target);
      var parent = target.parentElement;
      if (!big) {
        // Nav logo: replace with orwyx-full-logo.webp image
        var existing = parent.querySelector(":scope > [data-orwyx-ov]");
        if (existing) existing.remove();
        var logoOv = document.createElement("span");
        logoOv.setAttribute("data-orwyx-ov", "1");
        logoOv.style.cssText = "position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);display:flex;align-items:center;pointer-events:none";
        var logoImg = document.createElement("img");
        logoImg.src = "/assets/logos/orwyx-full-logo.webp";
        logoImg.alt = "Orwyx";
        logoImg.setAttribute("data-orwyx-brand-checked", "1");
        logoImg.style.cssText = "height:6rem;width:auto;max-width:none;object-fit:contain;display:block";
        logoOv.appendChild(logoImg);
        parent.appendChild(logoOv);
        // Wire the nearest <a> ancestor to always go to the home page hero
        var link = img.closest("a") || parent.closest("a");
        if (link && !link.getAttribute("data-orwyx-logo-wired")) {
          link.setAttribute("data-orwyx-logo-wired", "1");
          link.setAttribute("href", "/#start");
          link.addEventListener("click", function(e) {
            e.preventDefault();
            window.location.href = "/#start";
          });
        }
      } else {
        // Footer / large wordmark
        overlay(parent, wordmark("clamp(3.5rem,13vw,9rem)", "#121214"), "justify-content:center");
      }
    });
  }

  // =====================================
  // CONTACT WIDGET REPLACEMENT
  // =====================================
  // Replace the Calendly booking widget with plain WhatsApp/Email/Fiverr contact links —
  // hides the widget in place (never removes it) and overlays the link card, same safe
  // pattern as every other swap here.
  function contactRowHtml(href, ext, bg, iconSvg, title, sub) {
    return '<a href="' + href + '"' + (ext ? ' target="_blank" rel="noopener noreferrer"' : '') +
      ' class="orwyx-row" style="display:flex;align-items:center;gap:1rem;border:1px solid rgba(0,0,0,.1);background:rgba(0,0,0,.03);border-radius:0.85rem;padding:1rem;pointer-events:auto">' +
      '<span style="display:flex;flex-shrink:0;align-items:center;justify-content:center;width:2.75rem;height:2.75rem;border-radius:0.6rem;background:' +
      bg + ';color:#fff">' + iconSvg + '</span>' +
      '<span style="min-width:0;text-align:left"><span style="display:block;font-weight:700;font-size:0.9rem;color:#0a0a0a">' +
      title +
      '</span><span style="display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:0.75rem;color:#52525b">' +
      sub + '</span></span>' +
      '<svg width="13" height="13" viewBox="0 0 16 16" fill="none" aria-hidden="true" style="margin-left:auto;flex-shrink:0;color:#a1a1aa"><path d="M4 12L12 4m0 0H5.5M12 4v6.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"></path></svg>' +
      '</a>';
  }

  function contactLinksCardHtml() {
    var mailIcon =
      '<svg width="17" height="17" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.3"><rect x="1.5" y="3" width="13" height="10" rx="2"></rect><path d="M2 4.5L8 9l6-4.5" stroke-linecap="round"></path></svg>';
    var waIcon =
      '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a10 10 0 00-8.6 15.1L2 22l5-1.3A10 10 0 1012 2zm0 1.8a8.2 8.2 0 11-4.2 15.3l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 0112 3.8zm-3.1 4c-.2 0-.5.1-.7.3-.2.3-.9.9-.9 2.1s.9 2.4 1 2.6c.1.2 1.8 2.8 4.4 3.9 2.2.9 2.6.7 3.1.7.5-.1 1.5-.6 1.7-1.2.2-.6.2-1.1.2-1.2l-.4-.2-1.5-.7c-.2-.1-.4-.1-.5.1l-.7.8c-.1.2-.3.2-.5.1a6.7 6.7 0 01-3.3-2.9c-.1-.2 0-.4.1-.5l.5-.6c.1-.2.1-.3.2-.5v-.4L10 8.2c-.2-.4-.4-.4-.6-.4h-.5z"></path></svg>';
    var fiIcon = '<span style="font-weight:700;font-size:1.05rem">fi</span>';
    return '<div style="width:100%;background:#fff;border-radius:1.5rem;padding:2rem;pointer-events:auto;box-shadow:0 30px 80px rgba(0,0,0,.35)">' +
      '<p style="text-align:center;font-weight:700;font-size:1.2rem;color:#0a0a0a">Reach us directly</p>' +
      '<p style="margin-top:.4rem;text-align:center;font-size:0.85rem;color:#52525b">Pick whatever channel you prefer — the same person answers all three.</p>' +
      '<div style="display:flex;flex-direction:column;gap:1rem;margin-top:1.75rem">' +
      contactRowHtml("mailto:" + BRAND.email, false, "#0a0a0a", mailIcon, "Email us", BRAND
      .email) +
      contactRowHtml(BRAND.whatsapp, true, "#25D366", waIcon, "WhatsApp us", BRAND.phone) +
      contactRowHtml(BRAND.fiverr, true, "#1dbf73", fiIcon, "Hire us on Fiverr",
        "Top-rated seller · 1,400+ projects") +
      '</div>' +
      '<p style="display:flex;align-items:center;justify-content:center;gap:.5rem;margin-top:1.5rem;font-size:0.72rem;color:#71717a">We reply within 12 hours — usually much faster.</p>' +
      '</div>';
  }

  function swapContactWidget() {
    var widget = document.querySelector(".calendly-inline-widget");
    if (!widget || widget.getAttribute("data-orwyx-contact-checked")) return;
    widget.setAttribute("data-orwyx-contact-checked", "1");
    hideNode(widget);
    overlay(widget.parentElement, contactLinksCardHtml(),
      "align-items:flex-start;justify-content:center");
  }

  // =====================================
  // LINK FIXING + HASH-SCROLL
  // =====================================
  // Same-origin links to "/#id" land here as a fresh navigation; the target section is
  // client-rendered and may not exist in the DOM yet at load, so the browser's native
  // anchor-scroll silently does nothing. Poll briefly for the element and scroll manually.
  function fixHashScroll() {
    if (!location.hash) return;
    var id = location.hash.slice(1);
    var tries = 0;
    var iv = setInterval(function() {
      tries++;
      var el = document.getElementById(id);
      if (el) {
        clearInterval(iv);
        el.scrollIntoView({
          behavior: "smooth",
          block: "start"
        });
      } else if (tries > 40) {
        clearInterval(iv);
      }
    }, 150);
  }

  var LINKS = {
    "Work": "/work/",
    "Services": "/services/",
    "Pricing": "/pricing/",
    "About": "/about/",
    "Contact": "/contact/",
    "Home": "/",
    "Fiverr": BRAND.fiverr,
    "YouTube": BRAND.youtube
  };
  var TITLE = "Orwyx — Video Editing Agency | Short-Form, YouTube & Motion Design";

  function fixLinks() {
    // React metadata resets the tab title to amphora's on hydration — keep it Orwyx
    if (document.title !== TITLE) document.title = TITLE;
    // fully-rounded pill nav
    var nav = document.querySelector("body > nav");
    if (nav) {
      nav.classList.remove("rounded-2xl");
      nav.style.borderRadius = "9999px";
    }
    var mnav = document.querySelector("body > div nav");
    if (mnav) {
      mnav.classList.remove("rounded-2xl");
      mnav.style.borderRadius = "9999px";
    }
    // relabelled anchors -> correct section / external target
    document.querySelectorAll("nav a, footer a").forEach(function(a) {
      var t = (a.textContent || "").trim();
      if (Object.prototype.hasOwnProperty.call(LINKS, t)) {
        a.setAttribute("href", LINKS[t]);
        if (/^https?:/.test(LINKS[t])) {
          a.setAttribute("target", "_blank");
          a.setAttribute("rel", "noopener noreferrer");
        }
      }
    });
    // Contact detail links: href AND any visible text both come from BRAND, so editing
    // BRAND.email / BRAND.phone in one place is enough to update every mailto/tel link
    // on the page (footer, contact section, anywhere else one appears).
    document.querySelectorAll('a[href^="mailto:"]').forEach(function(a) {
      a.setAttribute("href", "mailto:" + BRAND.email);
      var label = a.querySelector("span");
      if (label && /@/.test(label.textContent || "")) label.textContent = BRAND.email;
    });
    document.querySelectorAll('a[href^="tel:"]').forEach(function(a) {
      a.setAttribute("href", "tel:" + BRAND.phoneDigits);
      var label = a.querySelector("span");
      if (label && /[+0-9]{6,}/.test(label.textContent || "")) label.textContent = BRAND.phone;
    });
    document.querySelectorAll('a[href*="google.com/maps"]').forEach(function(a) {
      a.setAttribute("href", "#calendly");
      a.removeAttribute("target");
    });
    document.querySelectorAll(
      'a[href="/impressum"], a[href="/datenschutz"], a[href^="impressum"], a[href^="datenschutz"]'
      ).forEach(function(a) {
      var t = (a.textContent || "").trim();
      a.setAttribute("href", LINKS[t] || "#calendly");
      if (/^https?:/.test(a.getAttribute("href"))) {
        a.setAttribute("target", "_blank");
        a.setAttribute("rel", "noopener noreferrer");
      }
    });
  }

  // The page ships two SEO "JSON-LD" <script> blocks (structured data that search
  // engines read to understand the business and the FAQ). The business one repeats the
  // same company name/email/phone that already live in BRAND above. Rather than keeping
  // a second hand-typed copy of those facts inside the JSON-LD text, this function
  // overwrites just those fields from BRAND every time it runs — so BRAND is the one
  // place to update the company name, email, or phone, and this schema always matches.
  function syncSeoSchemaWithBrand() {
    document.querySelectorAll('script[type="application/ld+json"]').forEach(function(tag) {
      var data;
      try {
        data = JSON.parse(tag.textContent);
      } catch (e) {
        return; // not valid JSON (or already patched into a shape we don't expect) - leave it alone
      }
      if (data["@type"] !== "ProfessionalService") return; // only the business schema has these fields
      data.name = BRAND.name;
      data.email = BRAND.email;
      data.telephone = BRAND.phoneDigits;
      data.url = BRAND.url;
      data.logo = BRAND.url + "icon.png";
      tag.textContent = JSON.stringify(data);
    });
  }

  // Debounced (not throttled): each mutation batch RESETS the timer, so this only fires
  // once things settle. childList only, scoped to <main> where possible, so this exists
  // purely to catch newly lazy-mounted sections, not every animation frame.
  var scheduleTimer = null;

  // Wire all hero CTA buttons (any variant text) to /contact/
  var CTA_BTN_TEXTS = [
    "Get your free quote", "Get in touch", "Talk to us",
    "Contact us", "Start a project"
  ];
  function patchCtaButtons() {
    document.querySelectorAll("button[type='button']").forEach(function(btn) {
      if (btn.getAttribute("data-orwyx-cta")) return;
      var text = (btn.textContent || "").trim();
      var match = CTA_BTN_TEXTS.some(function(t) { return text.indexOf(t) !== -1; });
      if (!match) return;
      btn.setAttribute("data-orwyx-cta", "1");
      btn.addEventListener("click", function(e) {
        e.preventDefault();
        e.stopImmediatePropagation();
        window.location.href = "/contact/";
      });
    });
  }

  function schedule() {
    if (scheduleTimer) clearTimeout(scheduleTimer);
    scheduleTimer = setTimeout(function() {
      scheduleTimer = null;
      if (observer) observer.disconnect();
      try {
        runSections();
        swapImages();
        swapBrandLogos();
        swapOrbitIcon();
        swapContactWidget();
        fixLinks();
        syncSeoSchemaWithBrand();
        injectLegal();
        patchStatsCard();
        patchFeastVideo();
        patchBeautyCard();
        disableBeautyCardClick();
        patchWorkGrid();
        patchPricingHero();
        patchPricingCards();
        patchPricingTitle();
        patchAboutPage();
        patchServicesPage();
        patchQualityCards();
        patchComparisonIcon();
        patchCtaButtons();
      } finally {
        observeRoot();
      }
    }, 180);
  }

  function observeRoot() {
    if (!observer) return;
    observer.observe(document.querySelector("main") || document.body, {
      childList: true,
      subtree: true
    });
  }

  // amphora's nav uses Next.js client-side routing (none of these routes exist in its
  // build), so it swallows clicks to the companion pages. Force a real navigation.
  var HARD_NAV_PREFIXES = ["/work/", "/services/", "/pricing/", "/about/", "/contact/", "/terms/",
    "/privacy/"
  ];

  function installHardNav() {
    if (window.__orwyxHardNav) return;
    window.__orwyxHardNav = true;
    document.addEventListener("click", function(e) {
      var a = e.target.closest && e.target.closest("a");
      if (!a) return;
      var h = a.getAttribute("href") || "";
      for (var i = 0; i < HARD_NAV_PREFIXES.length; i++) {
        if (h.indexOf(HARD_NAV_PREFIXES[i]) === 0) {
          e.preventDefault();
          e.stopImmediatePropagation();
          window.location.href = h;
          return;
        }
      }
    }, true);
  }

  // =====================================
  // STATS CARD PATCHES
  // =====================================
  function patchStatsCard() {
    var section = document.querySelector('#ergebnisse section');
    if (!section) return;
    var cols = section.querySelectorAll('.grid.grid-cols-1.md\\:grid-cols-3 > div');
    if (cols.length < 3) return;

    // ── Column 1: "50+" → "1,400+" at a larger size ──
    var statSpan = cols[0].querySelector('span.tabular-nums');
    if (statSpan && !statSpan.getAttribute('data-orwyx-stat-patched')) {
      statSpan.setAttribute('data-orwyx-stat-patched', '1');
      statSpan.textContent = '1,400+';
      statSpan.style.setProperty('font-size', 'clamp(3rem, 7vw, 5.5rem)', 'important');
      statSpan.style.lineHeight = '1';
      statSpan.style.paddingLeft = '0.06em';
      statSpan.style.marginLeft = '-0.06em';
      statSpan.style.paddingRight = '0.1em';
    }

    // ── Column 2: fill stars for 4.9/5 ──
    var starSvgs = cols[1].querySelectorAll('svg.overflow-visible');
    starSvgs.forEach(function(svg, i) {
      if (svg.getAttribute('data-orwyx-star-patched')) return;
      svg.setAttribute('data-orwyx-star-patched', '1');
      var bodyGrad = svg.querySelector('linearGradient[id^="star-body"]');
      if (!bodyGrad) return;
      var stops = bodyGrad.querySelectorAll('stop');
      if (i < 4) {
        // Fully filled — warm amber/gold
        if (stops[0]) stops[0].setAttribute('stop-color', '#FDE68A');
        if (stops[1]) stops[1].setAttribute('stop-color', '#F59E0B');
        if (stops[2]) stops[2].setAttribute('stop-color', '#D97706');
      } else {
        // 5th star: 90% filled left-to-right (4.9 out of 5)
        bodyGrad.setAttribute('x1', '1');
        bodyGrad.setAttribute('y1', '10');
        bodyGrad.setAttribute('x2', '19');
        bodyGrad.setAttribute('y2', '10');
        bodyGrad.setAttribute('gradientUnits', 'userSpaceOnUse');
        if (stops[0]) { stops[0].setAttribute('offset', '0%');  stops[0].setAttribute('stop-color', '#FDE68A'); }
        if (stops[1]) { stops[1].setAttribute('offset', '60%'); stops[1].setAttribute('stop-color', '#F59E0B'); }
        if (stops[2]) { stops[2].setAttribute('offset', '60%'); stops[2].setAttribute('stop-color', '#2a2622'); }
        var darkStop = document.createElementNS('http://www.w3.org/2000/svg', 'stop');
        darkStop.setAttribute('offset', '100%');
        darkStop.setAttribute('stop-color', '#0b0a08');
        bodyGrad.appendChild(darkStop);
      }
    });

    // ── Column 3: lighter quote text + bigger size + real avatar photo ──
    var card3 = cols[2].querySelector('.relative.flex.flex-col');
    if (!card3) return;

    // Quote paragraph — bold italic, large to fill card like reference
    var quoteP = card3.querySelector('p.font-serif');
    if (quoteP && !quoteP.getAttribute('data-orwyx-quote-patched-v5')) {
      quoteP.setAttribute('data-orwyx-quote-patched-v5', '1');
      quoteP.style.setProperty('font-weight', '400', 'important');
      quoteP.style.setProperty('font-size', 'clamp(1.15rem, 2.5vw, 1.5rem)', 'important');
      quoteP.style.lineHeight = '1.35';
    }

    // Avatar — replace initial with real photo
    var avatarWrap = card3.querySelector('.w-16.h-16.rounded-full');
    if (avatarWrap && !avatarWrap.getAttribute('data-orwyx-avatar-patched')) {
      avatarWrap.setAttribute('data-orwyx-avatar-patched', '1');
      avatarWrap.style.padding = '2px';
      var inner = avatarWrap.querySelector('.rounded-full.overflow-hidden.bg-gray-900');
      if (inner) {
        inner.style.boxShadow = 'inset 0 0 0 2px rgba(13,13,13,0.85)';
        inner.innerHTML = '<img src="/assets/abid-fareed-256.jpg" alt="Abid Fareed" data-orwyx-img-checked="1" style="width:100%;height:100%;object-fit:cover;object-position:50% 50%;" />';
      }
    }
  }

  // =====================================
  // FEAST SECTION: REPLACE SHOWREEL VIDEO WITH YOUTUBE EMBED
  // =====================================
  function patchFeastVideo() {
    var video = document.querySelector('.w-full.overflow-hidden.rounded-2xl.bg-black video');
    if (!video || video.getAttribute('data-orwyx-feast-patched')) return;
    video.setAttribute('data-orwyx-feast-patched', '1');
    var wrap = video.parentElement;
    if (!wrap) return;
    hideNode(video);
    var iframeId = 'orwyx-showreel-yt';
    var iframe = document.createElement('iframe');
    iframe.id = iframeId;
    // No loop=1/playlist — we handle looping via IFrame API seekTo(0) on ENDED
    // to avoid the brief YouTube UI flash that native loop causes at each cycle.
    iframe.setAttribute('src',
      'https://www.youtube-nocookie.com/embed/' + BRAND.showreelId +
      '?autoplay=1&mute=1&controls=0&playsinline=1&rel=0&modestbranding=1' +
      '&disablekb=1&iv_load_policy=3&showinfo=0&fs=0&enablejsapi=1');
    iframe.setAttribute('allow', 'autoplay; encrypted-media');
    iframe.setAttribute('frameborder', '0');
    iframe.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;border:0;';
    wrap.style.position = 'relative';
    // Cap height so device frame fits within the gradient section (no dark overflow below)
    var roundedContainer = wrap.parentElement;
    if (roundedContainer) roundedContainer.style.maxHeight = '520px';
    wrap.appendChild(iframe);
    var blocker = document.createElement('div');
    blocker.style.cssText = 'position:absolute;inset:0;z-index:1;cursor:default;';
    wrap.appendChild(blocker);
    // Handle seamless loop via IFrame API — no UI flash unlike native loop=1
    function initYTLoop() {
      new window.YT.Player(iframeId, {
        events: {
          onStateChange: function(e) {
            if (e.data === window.YT.PlayerState.ENDED) {
              e.target.seekTo(0);
              e.target.playVideo();
            }
          }
        }
      });
    }
    if (window.YT && window.YT.Player) {
      initYTLoop();
    } else {
      var prev = window.onYouTubeIframeAPIReady;
      window.onYouTubeIframeAPIReady = function() { if (prev) prev(); initYTLoop(); };
      if (!document.querySelector('script[src*="youtube.com/iframe_api"]')) {
        var s = document.createElement('script');
        s.src = 'https://www.youtube.com/iframe_api';
        document.head.appendChild(s);
      }
    }
  }

  // =====================================
  // WORK GRID: REPLACE CATEGORY LOGOS WITH VIDEO THUMBNAILS + TITLES
  // =====================================
  var WORK_VIDEOS = [
    { id: 'vHkDtdOiIbE', title: 'Wedding Project' },
    { id: '97Yx-ksMVks', title: 'The Road Map to Freedom' },
    { id: '9g08dMXNWYs', title: 'Manifestation' },
  ];

  function patchWorkGrid() {
    // Find a work grid card (parent: flex-col cursor-pointer, grandparent: the actual grid)
    var allCards = Array.from(document.querySelectorAll('[data-mockup-card="true"]'));
    var workCard = allCards.find(function(c) {
      var p = c.parentElement;
      return p && p.classList.contains('cursor-pointer') && p.classList.contains('flex-col');
    });
    if (!workCard) return;
    var grid = workCard.parentElement.parentElement;
    if (!grid || grid.getAttribute('data-orwyx-grid-patched')) return;
    grid.setAttribute('data-orwyx-grid-patched', '1');

    var cards = Array.from(grid.children);
    // Last card is the "You next?" CTA — wire it to /contact/
    var ctaCard = cards[cards.length - 1];
    if (ctaCard && !ctaCard.getAttribute('data-orwyx-cta-wired')) {
      ctaCard.setAttribute('data-orwyx-cta-wired', '1');
      ctaCard.style.cursor = 'pointer';
      ctaCard.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopImmediatePropagation();
        window.location.href = '/contact/';
      }, true);
    }
    var contentCards = cards.slice(0, cards.length - 1);

    contentCards.forEach(function(cardItem, i) {
      var v = WORK_VIDEOS[i];
      if (!v) return;
      var inner = cardItem.querySelector('.flex-1.bg-white.relative.overflow-hidden');
      if (!inner) return;

      Array.from(inner.children).forEach(function(el) { hideNode(el); });

      var img = document.createElement('img');
      img.src = 'https://i.ytimg.com/vi/' + v.id + '/hqdefault.jpg';
      img.alt = v.title;
      img.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;object-fit:cover;border:0;display:block;transform:scale(1.38) translateY(-6%);transform-origin:center center;';
      inner.appendChild(img);

      var titleBar = document.createElement('div');
      titleBar.style.cssText =
        'position:absolute;bottom:0;left:0;right:0;padding:5px 7px;z-index:1;pointer-events:none;' +
        'background:linear-gradient(to top,rgba(0,0,0,0.72) 0%,transparent 100%);';
      titleBar.innerHTML =
        '<span style="color:#fff;font-size:0.6rem;font-weight:600;line-height:1.2;' +
        'display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">' +
        v.title + '</span>';
      inner.appendChild(titleBar);
    });
  }

  // =====================================
  // ABOUT PAGE: overlays content into the real home-page sections (comparison + testimonials + contact)
  // so all animations, gradients, rounded corners, and scroll-reveals are 100% identical to home page.
  // =====================================
  function patchAboutPage() {
    if (!window.location.pathname.startsWith('/about')) return;

    // --- HERO ---
    var heroSection = document.querySelector('#start section');
    if (heroSection && !heroSection.getAttribute('data-orwyx-about-hero')) {
      heroSection.setAttribute('data-orwyx-about-hero', '1');
      heroSection.style.setProperty('background', '#020012', 'important');
      heroSection.style.setProperty('min-height', '54vh', 'important');
      heroSection.style.setProperty('padding-top', '8rem', 'important');
      heroSection.style.setProperty('padding-bottom', '0.5rem', 'important');
      var contentDiv = heroSection.querySelector('.relative.z-30');
      if (contentDiv) {
        contentDiv.style.cssText = 'display:flex;flex-direction:column;align-items:center;text-align:center;gap:1.25rem;position:relative;z-index:30';
        Array.from(contentDiv.children).forEach(function(c) {
          c.style.setProperty('display', 'none', 'important');
          c.setAttribute('data-orwyx-hidden', '1');
        });
        var h1 = document.createElement('h1');
        h1.setAttribute('data-orwyx-ov', '1');
        h1.style.cssText = 'font-size:clamp(2.6rem,5vw,4.8rem);font-weight:700;letter-spacing:-0.02em;line-height:1.15;color:#fff;margin:0;text-align:center';
        h1.innerHTML = 'Built in the edit,<br><span style="font-family:Georgia,serif;font-style:italic;font-weight:400;background:linear-gradient(90deg,#fb923c,#f87171,#f472b6);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text">one frame at a time.</span>';
        var p = document.createElement('p');
        p.setAttribute('data-orwyx-ov', '1');
        p.style.cssText = 'font-size:1rem;line-height:1.75;color:rgba(255,255,255,0.6);max-width:38rem;font-weight:300;margin:0;padding-bottom:3rem';
        p.textContent = 'Orwyx started in December 2018 as one freelance editor taking on projects nobody else wanted to rush. Seven years and 1,300+ videos later, it\'s a full post-production team trusted by creators, startups, and brands across 40+ industries.';
        contentDiv.appendChild(h1);
        contentDiv.appendChild(p);
        var heroObs = new MutationObserver(function() {
          Array.from(contentDiv.children).forEach(function(c) {
            if (!c.getAttribute('data-orwyx-ov') && !c.getAttribute('data-orwyx-hidden')) {
              c.style.setProperty('display', 'none', 'important');
              c.setAttribute('data-orwyx-hidden', '1');
            }
          });
        });
        heroObs.observe(contentDiv, { childList: true });
      }
    }

    // --- NEW: Inject CSS, data, and overlay content into real home-page sections ---

    if (!document.getElementById('orwyx-about-styles')) {
      var st = document.createElement('style');
      st.id = 'orwyx-about-styles';
      st.textContent =
        '.orwyx-val-card{position:relative;overflow:hidden;background:rgba(255,255,255,0.03);border-radius:20px;padding:2rem 1.75rem;display:flex;flex-direction:column;gap:0.875rem;transition:border-color .3s,background .3s,box-shadow .3s}' +
        '.orwyx-val-card-purple{border:1px solid rgba(139,92,246,0.28)}' +
        '.orwyx-val-card-purple:hover{background:rgba(139,92,246,0.07);border-color:rgba(139,92,246,0.62);box-shadow:0 0 38px rgba(139,92,246,0.22),0 8px 28px rgba(0,0,0,0.38)}' +
        '.orwyx-val-card-teal{border:1px solid rgba(20,184,166,0.28)}' +
        '.orwyx-val-card-teal:hover{background:rgba(20,184,166,0.07);border-color:rgba(20,184,166,0.62);box-shadow:0 0 38px rgba(20,184,166,0.22),0 8px 28px rgba(0,0,0,0.38)}' +
        '.orwyx-val-card-orange{border:1px solid rgba(249,115,22,0.28)}' +
        '.orwyx-val-card-orange:hover{background:rgba(249,115,22,0.07);border-color:rgba(249,115,22,0.62);box-shadow:0 0 38px rgba(249,115,22,0.22),0 8px 28px rgba(0,0,0,0.38)}' +
        '.orwyx-team-card{background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.09);border-radius:24px;padding:2.25rem 1.5rem 2rem;display:flex;flex-direction:column;align-items:center;text-align:center;gap:1.25rem;transition:background .2s,border-color .2s,transform .25s,box-shadow .25s}' +
        '.orwyx-team-card:hover{background:rgba(255,255,255,0.07);border-color:rgba(255,255,255,0.18);transform:translateY(-6px);box-shadow:0 24px 48px rgba(0,0,0,0.4)}' +
        '@media(max-width:700px){.orwyx-story-grid,.orwyx-val-grid,.orwyx-team-grid{grid-template-columns:1fr!important}}';
      document.head.appendChild(st);
    }

    var valCards = [
      {n:'01', t:'Craft over volume', d:'We\'d rather ship one edit that gets rewatched than ten that get skipped. Every frame earns its place — that bar doesn\'t move with deadline pressure.'},
      {n:'02', t:'Speed is a system', d:'48-72 hour delivery isn\'t heroics, it\'s process: structured intake, dedicated editors, and a feedback loop designed to converge, not circle.'},
      {n:'03', t:'Partners, not vendors', d:'We learn your audience, your voice, and your numbers. The tenth video should be meaningfully better than the first — that only happens in a real partnership.'}
    ];
    var team = [
      {i:'AF', name:'Abid Fareed',    role:'Founder & Lead Editor', c:'#f87171'},
      {i:'LK', name:'Laiba Khan',     role:'Video Editor',          c:'#fb923c'},
      {i:'QR', name:'Qayyum Raza',    role:'Video Editor',          c:'#f472b6'},
      {i:'NS', name:'Noman Shabbier', role:'UI/UX Designer',        c:'#34d399'},
      {i:'US', name:'Usama Saleem',   role:'Graphic Designer',      c:'#60a5fa'},
      {i:'AR', name:'Ahmad Raza',     role:'Graphic Designer',      c:'#a78bfa'}
    ];

    // --- UPPER DARK BLOCK: overlay about content in comparison (vergleich) section ---
    var vergleichEl = document.getElementById('vergleich');
    if (vergleichEl && !vergleichEl.getAttribute('data-orwyx-about-main')) {
      var vergleichSection = vergleichEl.querySelector('section');
      if (!vergleichSection) return;
      vergleichEl.setAttribute('data-orwyx-about-main', '1');
      var origContent = vergleichSection.children[1];
      if (origContent) {
        origContent.style.setProperty('display', 'none', 'important');
        origContent.setAttribute('data-orwyx-hidden', '1');
      }
      // Keep children[0] (gradient orb decorations) visible — only collapse the comparison table
      vergleichSection.style.setProperty('padding-top', '0', 'important');
      vergleichSection.style.setProperty('padding-bottom', '5rem', 'important');

      var aboutDiv = document.createElement('div');
      aboutDiv.setAttribute('data-orwyx-ov', '1');
      aboutDiv.style.cssText = 'position:relative;z-index:10;max-width:72rem;margin:0 auto;padding:1rem 2rem 5rem';
      // Full-width blob background layer appended directly to vergleichSection
      var blobBg = document.createElement('div');
      blobBg.setAttribute('aria-hidden', 'true');
      blobBg.setAttribute('data-orwyx-ov', '1');
      blobBg.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:60%;pointer-events:none;overflow:hidden;z-index:2';
      blobBg.innerHTML =
        '<div style="position:absolute;left:-5%;top:8%;width:38%;height:55%;background:radial-gradient(ellipse at center,rgba(139,92,246,0.38) 0%,transparent 68%);filter:blur(55px)"></div>' +
        '<div style="position:absolute;right:-4%;top:25%;width:32%;height:45%;background:radial-gradient(ellipse at center,rgba(251,146,60,0.28) 0%,transparent 68%);filter:blur(50px)"></div>' +
        '<div style="position:absolute;left:12%;bottom:10%;width:26%;height:35%;background:radial-gradient(ellipse at center,rgba(96,165,250,0.24) 0%,transparent 68%);filter:blur(65px)"></div>' +
        '<div style="position:absolute;right:22%;top:3%;width:28%;height:28%;background:radial-gradient(ellipse at center,rgba(244,114,182,0.22) 0%,transparent 68%);filter:blur(45px)"></div>' +
        '<div style="position:absolute;inset:0;opacity:0.045;background-image:url(\'data:image/svg+xml,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22200%22%20height%3D%22200%22%3E%3Cfilter%20id%3D%22n%22%3E%3CfeTurbulence%20type%3D%22fractalNoise%22%20baseFrequency%3D%220.65%22%20numOctaves%3D%223%22%20stitchTiles%3D%22stitch%22%2F%3E%3C%2Ffilter%3E%3Crect%20width%3D%22200%22%20height%3D%22200%22%20filter%3D%22url(%23n)%22%2F%3E%3C%2Fsvg%3E\');background-size:200px 200px"></div>';
      vergleichSection.appendChild(blobBg);

      aboutDiv.innerHTML = [
        '<p style="position:relative;font-size:0.65rem;font-weight:700;letter-spacing:0.22em;text-transform:uppercase;color:rgba(255,255,255,0.3);margin:0 0 3rem">ABOUT ORWYX</p>',
        '<div class="orwyx-story-grid" style="display:grid;grid-template-columns:1fr 1fr;gap:4rem;align-items:start;margin-bottom:5rem">',
          '<h2 style="font-size:clamp(1.75rem,2.8vw,2.625rem);font-weight:700;line-height:1.2;color:#fff;margin:0">From freelancer to agency<br>without losing<br>the obsession.</h2>',
          '<div style="display:flex;flex-direction:column;gap:1.25rem">',
            '<p style="font-size:0.9375rem;line-height:1.8;color:rgba(255,255,255,0.58);margin:0">Most agencies start with a pitch deck. Ours started with deadlines: years of marketplace work where the only marketing was the last delivery. That history shaped everything - the turnaround discipline, the communication habits, and the belief that the work itself is the sales team.</p>',
            '<p style="font-size:0.9375rem;line-height:1.8;color:rgba(255,255,255,0.58);margin:0">Today Orwyx runs as an editor-led studio: every project has a dedicated editor, every edit gets a second pair of eyes, and every client gets the same 48-72 hour pulse that built our reputation.</p>',
          '</div>',
        '</div>',
        '<div class="orwyx-val-grid" style="display:grid;grid-template-columns:repeat(3,1fr);gap:1.25rem;margin-bottom:5rem">',
          (function() {
            var NOISE = 'data:image/svg+xml,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22150%22%20height%3D%22150%22%3E%3Cfilter%20id%3D%22n%22%3E%3CfeTurbulence%20type%3D%22fractalNoise%22%20baseFrequency%3D%220.65%22%20numOctaves%3D%223%22%20stitchTiles%3D%22stitch%22%2F%3E%3C%2Ffilter%3E%3Crect%20width%3D%22150%22%20height%3D%22150%22%20filter%3D%22url(%23n)%22%2F%3E%3C%2Fsvg%3E';
            var cols = [
              {cls:'orwyx-val-card-purple', r:'139,92,246', bp:'top:-40%;left:-20%'},
              {cls:'orwyx-val-card-teal',   r:'20,184,166', bp:'top:-35%;right:-15%'},
              {cls:'orwyx-val-card-orange', r:'249,115,22', bp:'bottom:-30%;right:-20%'}
            ];
            return valCards.map(function(v, i) {
              var c = cols[i];
              return '<div class="orwyx-val-card ' + c.cls + '">' +
                '<div aria-hidden="true" style="position:absolute;' + c.bp + ';width:85%;height:75%;background:radial-gradient(ellipse at center,rgba(' + c.r + ',0.32) 0%,transparent 65%);filter:blur(36px);pointer-events:none;z-index:0"></div>' +
                '<div aria-hidden="true" style="position:absolute;inset:0;opacity:0.06;background-image:url(\'' + NOISE + '\');background-size:150px 150px;pointer-events:none;z-index:0"></div>' +
                '<span style="position:relative;z-index:1;font-size:0.65rem;font-weight:700;letter-spacing:0.18em;color:rgba(255,255,255,0.22)">' + v.n + '</span>' +
                '<h3 style="position:relative;z-index:1;font-size:1.0625rem;font-weight:700;color:#fff;margin:0">' + v.t + '</h3>' +
                '<p style="position:relative;z-index:1;font-size:0.875rem;line-height:1.7;color:rgba(255,255,255,0.52);margin:0">' + v.d + '</p>' +
                '</div>';
            }).join('');
          })(),
        '</div>',
        '<div style="height:1px;background:linear-gradient(90deg,transparent,rgba(255,255,255,0.1),transparent);margin-bottom:5rem"></div>',
        '<p style="font-size:0.65rem;font-weight:700;letter-spacing:0.22em;text-transform:uppercase;color:rgba(255,255,255,0.3);margin:0 0 1rem">THE TEAM</p>',
        '<h2 style="font-size:clamp(2rem,3.5vw,2.875rem);font-weight:800;color:#fff;margin:0 0 0.75rem;letter-spacing:-0.02em">Small team. Full stack.</h2>',
        '<p style="font-size:0.9375rem;line-height:1.7;color:rgba(255,255,255,0.5);max-width:34rem;margin:0 0 3rem">Editors, motion designers, and visual designers working as one pipeline - no hand-off gaps, no telephone game.</p>',
        '<div class="orwyx-team-grid" style="display:grid;grid-template-columns:repeat(3,1fr);gap:1.5rem">',
          team.map(function(m) {
            return '<div class="orwyx-team-card">' +
              '<div style="width:96px;height:96px;border-radius:9999px;background:' + m.c + '18;border:2.5px solid ' + m.c + '55;box-shadow:0 0 0 6px ' + m.c + '10;display:flex;align-items:center;justify-content:center">' +
              '<span style="font-size:1.625rem;font-weight:800;color:' + m.c + '">' + m.i + '</span>' +
              '</div>' +
              '<div><p style="font-size:1.0625rem;font-weight:700;color:#fff;margin:0 0 0.25rem">' + m.name + '</p>' +
              '<p style="font-size:0.8125rem;color:rgba(255,255,255,0.42);margin:0">' + m.role + '</p></div>' +
              '</div>';
          }).join(''),
        '</div>',
      ].join('');
      vergleichSection.appendChild(aboutDiv);

      var mainObs = new MutationObserver(function() {
        if (origContent && !origContent.getAttribute('data-orwyx-hidden')) {
          origContent.style.setProperty('display', 'none', 'important');
          origContent.setAttribute('data-orwyx-hidden', '1');
        }
      });
      mainObs.observe(vergleichSection, { childList: true });
    }

    // --- TESTIMONIALS: kept exactly as home page via PAGE_KEEP ---

    // --- CTA BLOCK: overlay CTA into contact (calendly) section ---
    var calendlyEl = document.getElementById('calendly');
    if (calendlyEl && !calendlyEl.getAttribute('data-orwyx-about-cta')) {
      var calSection = calendlyEl.querySelector('section') || calendlyEl.firstElementChild;
      if (!calSection) return;
      calendlyEl.setAttribute('data-orwyx-about-cta', '1');
      var calContent = calSection.children[2];
      if (calContent) {
        calContent.style.setProperty('display', 'none', 'important');
        calContent.setAttribute('data-orwyx-hidden', '1');
      }

      // calendlyEl already has rounded-t-[80px] (top corners) — keep it.
      // Use clip-path on calSection for bottom rounding so CSS filter:blur() is clipped
      // reliably at the curve (overflow:hidden alone doesn't clip filter effects in all browsers).
      calendlyEl.style.setProperty('background', 'transparent', 'important');
      calSection.style.setProperty('border-radius', '0', 'important');
      calSection.style.setProperty('clip-path', 'inset(0 0 0 0 round 0px 0px 80px 80px)', 'important');
      calSection.style.setProperty('overflow', 'hidden', 'important');
      calSection.style.setProperty('position', 'relative', 'important');
      calSection.style.setProperty('padding-bottom', '0', 'important');

      // Full-section gradient container: color at TOP and BOTTOM edges, dark in the center.
      // Both gradients fade toward transparent so the dark section bg shows in the middle.
      var ctaGradients = document.createElement('div');
      ctaGradients.setAttribute('aria-hidden', 'true');
      ctaGradients.setAttribute('data-orwyx-ov', '1');
      ctaGradients.style.cssText = 'position:absolute;inset:0;pointer-events:none;z-index:1;overflow:hidden;transform:translateZ(0)';
      ctaGradients.innerHTML =
        // TOP base gradient: colored at top edge → transparent toward center
        '<div style="position:absolute;top:0;left:0;right:0;height:55%;background:linear-gradient(to bottom,rgba(109,40,217,0.40) 0%,rgba(157,23,77,0.15) 50%,transparent 100%);filter:blur(48px)"></div>' +
        // TOP color bar: sits above the section, blur bleeds down into top area
        '<div style="position:absolute;top:-100px;left:50%;transform:translateX(-50%);width:80%;height:300px;background:linear-gradient(90deg,#6d28d9,#be185d,#dc2626,#ea580c);filter:blur(100px);opacity:0.45"></div>' +
        // BOTTOM base gradient: colored at bottom edge → transparent toward center
        '<div style="position:absolute;bottom:0;left:0;right:0;height:55%;background:linear-gradient(to top,rgba(109,40,217,0.45) 0%,rgba(157,23,77,0.20) 50%,transparent 100%);filter:blur(48px)"></div>' +
        // BOTTOM color bar: sits below the section, blur bleeds up into bottom area
        '<div style="position:absolute;bottom:-100px;left:50%;transform:translateX(-50%);width:80%;height:350px;background:linear-gradient(90deg,#6d28d9,#be185d,#dc2626,#ea580c);filter:blur(100px);opacity:0.55"></div>' +
        // Noise texture across full section
        '<div style="position:absolute;inset:0;opacity:0.08;background-image:url(\'/assets/textures/noise-128.webp\');background-size:128px 128px"></div>';
      calSection.appendChild(ctaGradients);

      var ctaDiv = document.createElement('div');
      ctaDiv.setAttribute('data-orwyx-ov', '1');
      // Reduced height: less overall padding; extra bottom space for the blobs below
      ctaDiv.style.cssText = 'position:relative;z-index:10;text-align:center;padding:3.5rem 2rem 7rem;max-width:52rem;margin:0 auto';
      ctaDiv.innerHTML =
        '<p style="font-size:0.65rem;font-weight:700;letter-spacing:0.22em;text-transform:uppercase;color:rgba(255,255,255,0.3);margin:0 0 1.5rem">NO DOWNTIME - BOOKING YEAR-ROUND</p>' +
        '<h2 style="font-size:clamp(2.5rem,5vw,4rem);font-weight:800;color:#fff;line-height:1.1;letter-spacing:-0.03em;margin:0 0 2.25rem">Your story deserves<br>more than an edit.</h2>' +
        '<a href="/contact/" style="display:inline-flex;align-items:center;gap:0.625rem;padding:0.875rem 2rem;border-radius:9999px;background:rgba(255,255,255,0.07);border:1px solid rgba(255,255,255,0.18);color:#fff;font-size:0.9375rem;font-weight:600;text-decoration:none;letter-spacing:0.01em;transition:background .2s,border-color .2s" onmouseover="this.style.background=\'rgba(255,255,255,0.12)\';this.style.borderColor=\'rgba(255,255,255,0.35)\'" onmouseout="this.style.background=\'rgba(255,255,255,0.07)\';this.style.borderColor=\'rgba(255,255,255,0.18)\'">Start a project</a>';
      calSection.appendChild(ctaDiv);

      var ctaObs = new MutationObserver(function() {
        if (calContent && !calContent.getAttribute('data-orwyx-hidden')) {
          calContent.style.setProperty('display', 'none', 'important');
          calContent.setAttribute('data-orwyx-hidden', '1');
        }
      });
      ctaObs.observe(calSection, { childList: true });
    }
  }

  // =====================================
  // SERVICES PAGE: HERO + BIG DARK CONTENT BLOCK
  // =====================================
  function svcIconPhone() {
    return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><circle cx="12" cy="17" r="1" fill="rgba(255,255,255,0.7)" stroke="none"/></svg>';
  }
  function svcIconPlay() {
    return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="15" rx="2"/><polygon points="10 8 16 11 10 14 10 8" fill="rgba(255,255,255,0.7)" stroke="rgba(255,255,255,0.7)" stroke-width="1"/></svg>';
  }
  function svcIconStar() {
    return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>';
  }
  function svcIconTrend() {
    return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>';
  }
  function svcIconMic() {
    return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="2" width="6" height="11" rx="3"/><path d="M5 10a7 7 0 0014 0"/><line x1="12" y1="21" x2="12" y2="17"/><line x1="8" y1="21" x2="16" y2="21"/></svg>';
  }
  function svcIconTeam() {
    return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/></svg>';
  }
  function svcCard(num, icon, title, desc) {
    return '<div data-orwyx-ov="1" ' +
      'style="background:radial-gradient(ellipse at top left,rgba(109,40,217,0.10) 0%,transparent 55%),radial-gradient(ellipse at bottom right,rgba(190,24,93,0.07) 0%,transparent 55%),rgba(255,255,255,0.025);border:1px solid rgba(109,40,217,0.25);border-radius:20px;padding:1.75rem;transition:box-shadow .3s,border-color .3s;cursor:default;position:relative;z-index:2" ' +
      'onmouseover="this.style.boxShadow=\'0 0 40px rgba(109,40,217,0.22),0 0 80px rgba(190,24,93,0.10)\';this.style.borderColor=\'rgba(190,24,93,0.45)\'" ' +
      'onmouseout="this.style.boxShadow=\'\';this.style.borderColor=\'rgba(109,40,217,0.25)\'">' +
      '<div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:1.25rem">' +
      '<div style="width:40px;height:40px;border-radius:10px;background:rgba(255,255,255,0.07);display:flex;align-items:center;justify-content:center">' + icon + '</div>' +
      '<span style="font-size:.7rem;font-weight:600;color:rgba(255,255,255,0.2);letter-spacing:.1em">' + num + '</span>' +
      '</div>' +
      '<h3 style="font-size:1.125rem;font-weight:700;color:#fff;margin:0 0 .6rem;line-height:1.25">' + title + '</h3>' +
      '<p style="font-size:.875rem;color:rgba(255,255,255,0.5);margin:0 0 1.5rem;line-height:1.6">' + desc + '</p>' +
      '<span style="font-size:.8rem;font-weight:600;color:rgba(255,255,255,0.35);display:inline-flex;align-items:center;gap:.3rem">Explore ' +
      '<svg width="11" height="11" viewBox="0 0 12 12" fill="none"><path d="M2 10L10 2M10 2H4M10 2v6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
      '</span></div>';
  }

  function patchServicesPage() {
    if (!window.location.pathname.startsWith('/services')) return;

    // ── HERO ─────────────────────────────────────────────────────────────────
    var heroSection = document.querySelector('#start section');
    if (heroSection && !heroSection.getAttribute('data-orwyx-svc-hero')) {
      heroSection.setAttribute('data-orwyx-svc-hero', '1');
      heroSection.style.setProperty('min-height', '48vh', 'important');
      heroSection.style.setProperty('padding-top', '8rem', 'important');
      heroSection.style.setProperty('padding-bottom', '2.5rem', 'important');
      var contentDiv = heroSection.querySelector('.relative.z-30');
      if (contentDiv) {
        contentDiv.style.cssText = 'display:flex;flex-direction:column;align-items:center;text-align:center;gap:1.25rem;position:relative;z-index:30';
        Array.from(contentDiv.children).forEach(function(c) {
          c.style.setProperty('display', 'none', 'important');
          c.setAttribute('data-orwyx-hidden', '1');
        });
        var h1 = document.createElement('h1');
        h1.setAttribute('data-orwyx-ov', '1');
        h1.style.cssText = 'font-size:clamp(2.6rem,5vw,4.8rem);font-weight:700;letter-spacing:-0.02em;line-height:1.15;color:#fff;margin:0';
        h1.innerHTML = 'Everything after<br>the <span style="font-family:Georgia,serif;font-style:italic;font-weight:400;background:linear-gradient(90deg,#fb923c,#f87171,#f472b6);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text">record button.</span>';
        var p = document.createElement('p');
        p.setAttribute('data-orwyx-ov', '1');
        p.style.cssText = 'font-size:1rem;line-height:1.7;color:rgba(255,255,255,0.65);max-width:38rem;font-weight:300;margin:0;padding-bottom:3rem';
        p.textContent = 'From a single hero video to a full monthly content engine — six disciplines run by one team, on one quality bar.';
        contentDiv.appendChild(h1);
        contentDiv.appendChild(p);
        var heroObs = new MutationObserver(function() {
          Array.from(contentDiv.children).forEach(function(c) {
            if (!c.getAttribute('data-orwyx-ov') && !c.getAttribute('data-orwyx-hidden')) {
              c.style.setProperty('display', 'none', 'important');
              c.setAttribute('data-orwyx-hidden', '1');
            }
          });
        });
        heroObs.observe(contentDiv, { childList: true });
      }
    }

    // ── MAIN CONTENT BLOCK ────────────────────────────────────────────────────
    if (document.getElementById('orwyx-svc-block')) return;
    var anwendungen = document.getElementById('anwendungen');
    var ablauf = document.getElementById('ablauf');
    var vorteile = document.getElementById('vorteile');
    var start = document.getElementById('start');
    if (!anwendungen || !ablauf || !vorteile || !start) return;

    // Outer transparent shell — same pattern as #calendly on about page
    var shell = document.createElement('div');
    shell.id = 'orwyx-svc-block';
    shell.setAttribute('data-orwyx-ov', '1');
    shell.style.cssText = 'position:relative;background:transparent';
    start.parentElement.insertBefore(shell, start.nextSibling);

    // Inner dark block: all-4-corner rounding, clip-path for reliable filter clipping
    var inner = document.createElement('div');
    inner.setAttribute('data-orwyx-ov', '1');
    inner.style.cssText = 'position:relative;background:#050505;clip-path:inset(0 0 0 0 round 80px);overflow:hidden';
    shell.appendChild(inner);

    // Move sections into inner in desired visual order: What We Do → How It Works → 5 Promises (content hidden)
    inner.appendChild(anwendungen);
    inner.appendChild(ablauf);
    inner.appendChild(vorteile);

    // Hide all children of vorteile but keep the element for block height
    Array.from(vorteile.children).forEach(function(child) {
      child.style.setProperty('display', 'none', 'important');
    });
    vorteile.style.setProperty('min-height', '0', 'important');
    new MutationObserver(function() {
      Array.from(vorteile.children).forEach(function(c) {
        c.style.setProperty('display', 'none', 'important');
      });
    }).observe(vorteile, { childList: true });

    // Give sections position:relative so they stack above the gradient overlay (z-index:1)
    [anwendungen, ablauf, vorteile].forEach(function(el) {
      el.style.setProperty('position', 'relative', 'important');
      el.style.setProperty('z-index', '2', 'important');
    });

    // ── "WHAT WE DO" OVERLAY on #anwendungen ─────────────────────────────────
    anwendungen.style.setProperty('min-height', 'auto', 'important');
    Array.from(anwendungen.children).forEach(function(child) {
      child.style.setProperty('display', 'none', 'important');
      child.setAttribute('data-orwyx-hidden', '1');
    });
    var wwd = document.createElement('div');
    wwd.setAttribute('data-orwyx-ov', '1');
    wwd.style.cssText = 'padding:48rem 2rem 4rem;max-width:76rem;margin:0 auto;position:relative;z-index:10';
    wwd.innerHTML = [
      '<p style="font-size:.65rem;font-weight:700;letter-spacing:.22em;text-transform:uppercase;color:rgba(255,255,255,0.3);margin:0 0 1.5rem">WHAT WE DO</p>',
      '<h2 style="font-size:clamp(2.5rem,5vw,3.75rem);font-weight:800;color:#fff;line-height:1.1;letter-spacing:-.03em;margin:0 0 1.25rem">One partner.<br>Every frame <span style="background:linear-gradient(90deg,#f97316,#db2777);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text">covered.</span></h2>',
      '<p style="font-size:1rem;color:rgba(255,255,255,0.5);max-width:40rem;margin:0 0 3.5rem;line-height:1.7">Six disciplines, one production system — so your short-form, long-form, and motion design all speak the same visual language.</p>',
      '<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:1rem">',
      svcCard('01', svcIconPhone(), 'Short-Form', 'Reels, TikToks, and Shorts engineered to stop thumbs.'),
      svcCard('02', svcIconPlay(), 'Long-Form &amp; YouTube', 'Long-form edits engineered for watch-time and retention.'),
      svcCard('03', svcIconStar(), 'Motion Design', 'Brand motion systems, intros, and animation with real polish.'),
      svcCard('04', svcIconTrend(), 'Video Ads', 'Paid-social creative built to convert, not just to look good.'),
      svcCard('05', svcIconMic(), 'Podcast', 'Full-stack podcast post: episodes, clips, and audiograms.'),
      svcCard('06', svcIconTeam(), 'For Agencies', 'White-label post-production capacity for agencies and studios.'),
      '</div>',
    ].join('');
    anwendungen.appendChild(wwd);
    var wwdObs = new MutationObserver(function() {
      Array.from(anwendungen.children).forEach(function(c) {
        if (!c.getAttribute('data-orwyx-ov') && !c.getAttribute('data-orwyx-hidden')) {
          c.style.setProperty('display', 'none', 'important');
          c.setAttribute('data-orwyx-hidden', '1');
        }
      });
    });
    wwdObs.observe(anwendungen, { childList: true });

    // Bottom spacer so content doesn't cut off at the rounded corner
    var spacer = document.createElement('div');
    spacer.setAttribute('data-orwyx-ov', '1');
    spacer.style.cssText = 'height:22rem;position:relative;z-index:2';
    inner.appendChild(spacer);

    // ── GRADIENT OVERLAYS (top + bottom of inner) ─────────────────────────────
    var gradients = document.createElement('div');
    gradients.setAttribute('aria-hidden', 'true');
    gradients.setAttribute('data-orwyx-ov', '1');
    gradients.style.cssText = 'position:absolute;inset:0;pointer-events:none;z-index:1;overflow:hidden;transform:translateZ(0)';
    gradients.innerHTML = [
      '<div style="position:absolute;top:0;left:0;right:0;height:55%;background:linear-gradient(to bottom,rgba(29,78,216,0.45) 0%,rgba(67,56,202,0.20) 50%,transparent 100%);filter:blur(48px)"></div>',
      '<div style="position:absolute;top:-80px;left:50%;transform:translateX(-50%);width:90%;height:380px;background:linear-gradient(90deg,#0ea5e9,#3b82f6,#4f46e5,#7c3aed);filter:blur(120px);opacity:0.40"></div>',
      '<div style="position:absolute;bottom:0;left:0;right:0;height:20%;background:linear-gradient(to top,rgba(109,40,217,0.35) 0%,transparent 100%);filter:blur(48px)"></div>',
      '<div style="position:absolute;bottom:60px;left:50%;transform:translateX(-50%);width:90%;height:340px;background:linear-gradient(90deg,#6d28d9,#be185d,#dc2626,#ea580c);filter:blur(110px);opacity:0.45"></div>',
      '<div style="position:absolute;inset:0;opacity:0.06;background-image:url(\'/assets/textures/noise-128.webp\');background-size:128px 128px"></div>',
    ].join('');
    inner.appendChild(gradients);
  }

  // =====================================
  // PRICING HERO: REPLACE WITH CLEAN CENTERED TITLE + PARAGRAPH
  // =====================================
  function patchPricingHero() {
    if (!window.location.pathname.startsWith('/pricing')) return;
    var heroSection = document.querySelector('#start section');
    if (!heroSection || heroSection.getAttribute('data-orwyx-pricing-hero')) return;
    heroSection.setAttribute('data-orwyx-pricing-hero', '1');

    // Shrink section height to half
    heroSection.style.setProperty('min-height', '48vh', 'important');
    heroSection.style.setProperty('padding-top', '8rem', 'important');
    heroSection.style.setProperty('padding-bottom', '2.5rem', 'important');

    // Find and replace the content container (direct z-30 child div)
    var contentDiv = heroSection.querySelector('.relative.z-30');
    if (!contentDiv) return;

    contentDiv.style.cssText = 'display:flex;flex-direction:column;align-items:center;text-align:center;gap:1.25rem;position:relative;z-index:30';

    // Hide all existing React-managed children (left column, right tablet column, etc.)
    Array.from(contentDiv.children).forEach(function(c) {
      c.style.setProperty('display', 'none', 'important');
      c.setAttribute('data-orwyx-hidden', '1');
    });

    // Append clean centered content as new nodes (don't replace innerHTML — React fights back)
    var h1 = document.createElement('h1');
    h1.setAttribute('data-orwyx-ov', '1');
    h1.style.cssText = 'font-size:clamp(2.6rem,5vw,4.8rem);font-weight:700;letter-spacing:-0.02em;line-height:1.15;color:#fff;margin:0';
    h1.innerHTML = 'Simple models.<br><span style="font-family:Georgia,serif;font-style:italic;font-weight:400;background:linear-gradient(90deg,#fb923c,#f87171,#f472b6);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text">Serious output.</span>';

    var p = document.createElement('p');
    p.setAttribute('data-orwyx-ov', '1');
    p.style.cssText = 'font-size:1rem;line-height:1.7;color:rgba(255,255,255,0.65);max-width:36rem;font-weight:300;margin:0;padding-bottom:3rem';
    p.textContent = 'Pick the engagement that matches how you publish. Every model ships with the same editors, the same quality bar, and the same 48–72h rhythm.';

    contentDiv.appendChild(h1);
    contentDiv.appendChild(p);

    // Guard against React re-adding children after hydration
    var heroObs = new MutationObserver(function() {
      Array.from(contentDiv.children).forEach(function(c) {
        if (!c.getAttribute('data-orwyx-ov') && !c.getAttribute('data-orwyx-hidden')) {
          c.style.setProperty('display', 'none', 'important');
          c.setAttribute('data-orwyx-hidden', '1');
        }
      });
    });
    heroObs.observe(contentDiv, { childList: true });
  }

  // =====================================
  // PRICING TITLE: ADD SPACE BEFORE "catch?"
  // =====================================
  function patchPricingTitle() {
    var preis = document.getElementById('preis');
    if (!preis || preis.getAttribute('data-orwyx-title-patched')) return;
    var h2 = preis.querySelector('h2');
    if (!h2) return;
    var catchSpan = Array.from(h2.querySelectorAll('span')).find(function(s) {
      return s.textContent.trim() === 'catch?';
    });
    if (!catchSpan || catchSpan.getAttribute('data-orwyx-catch-spaced')) return;
    catchSpan.setAttribute('data-orwyx-catch-spaced', '1');
    catchSpan.style.marginLeft = '0.12em';
    preis.setAttribute('data-orwyx-title-patched', '1');
  }

  // =====================================
  // PRICING CARDS: REMOVE MOTION DESIGN + FIX FONT SIZES
  // =====================================
  function patchPricingCards() {
    var grid = document.querySelector('#preis .grid.sm\\:grid-cols-2');
    if (!grid) return;

    // Responsive column logic — always runs so pre-baked HTML is corrected on load
    if (!grid._orwyxColsWired) {
      grid._orwyxColsWired = true;
      function applyPricingGridCols() {
        if (window.matchMedia('(min-width: 640px)').matches) {
          grid.style.setProperty('grid-template-columns', 'repeat(3, minmax(0, 1fr))', 'important');
        } else {
          grid.style.setProperty('grid-template-columns', '1fr', 'important');
        }
      }
      applyPricingGridCols();
      window.addEventListener('resize', applyPricingGridCols);
      if (window.ResizeObserver) {
        new ResizeObserver(applyPricingGridCols).observe(document.documentElement);
      }
    }

    // One-time DOM mutations — skip if already patched (pre-baked HTML)
    if (grid.getAttribute('data-orwyx-pricing-patched')) return;
    grid.setAttribute('data-orwyx-pricing-patched', '1');

    var cards = Array.from(grid.children);

    // Remove Motion Design card (index 2) — hide in place, never remove React nodes
    if (cards[2]) {
      cards[2].style.display = 'none';
    }

    // Switch grid from 4 cols to 3 cols
    grid.classList.remove('lg:grid-cols-4');
    grid.classList.add('lg:grid-cols-3');

    // Fix heading italic span size (33.6px → match h3's 30px white text)
    cards.forEach(function(card, i) {
      if (i === 2) return; // skip hidden card
      // Boost pricing/quote cursive spans — those with text-transparent (gradient text)
      // but NOT inside an h3 (those are heading labels, handled separately below)
      card.querySelectorAll('.font-serif.italic.text-transparent').forEach(function(sp) {
        if (!sp.closest('h3')) {
          sp.style.setProperty('font-size', '2rem', 'important');
          sp.style.setProperty('line-height', '1.1', 'important');
        }
      });
      // Shrink h3 italic spans to match the surrounding white text (1em = same as h3)
      var h3 = card.querySelector('h3');
      if (h3) {
        h3.querySelectorAll('span').forEach(function(sp) {
          if (getComputedStyle(sp).fontStyle === 'italic') {
            sp.style.setProperty('font-size', '1em', 'important');
          }
        });
      }
      // Add breathing room above the CTA button at the bottom of each card
      var inner = card.querySelector('.flex.flex-col');
      if (inner) {
        var btn = inner.querySelector('a[href], button');
        if (btn) {
          btn.style.setProperty('margin-top', '8rem', 'important');
          if (!btn.getAttribute('data-orwyx-cta')) {
            btn.setAttribute('data-orwyx-cta', '1');
            if (btn.tagName === 'A') {
              btn.setAttribute('href', '/contact/');
              btn.removeAttribute('target');
              btn.removeAttribute('rel');
            }
            btn.addEventListener('click', function(e) {
              e.preventDefault();
              e.stopImmediatePropagation();
              window.location.href = '/contact/';
            });
          }
        }
      }
    });
  }

  // =====================================
  // QUALITY CARDS: FIX TEXT SPACING + LINE BREAKS
  // =====================================
  // Actual h3 structures found in the DOM (each card has two: small orbit card + large expanded):
  //   "Fixed price": <h3><span italic>Fixed price</span>from day one</h3>
  //   "ownership":   <h3>Full<br><span italic>ownership</span>forever</h3>  (small)
  //                  <h3>Full <span italic>ownership</span>forever</h3>      (large)
  //   "revisions":   <h3>Unlimited<span italic>revisions</span></h3>
  // The italic span holds the FIRST word; plain text follows as a sibling text node.
  function patchQualityCards() {
    var qualRoot = findContainer('Quality promises');
    if (!qualRoot) return;

    qualRoot.querySelectorAll('h3').forEach(function(h3) {
      if (h3.getAttribute('data-orwyx-qch3-patched')) return;
      var mainSpan = h3.querySelector('span');
      if (!mainSpan) return;
      var spanText = (mainSpan.textContent || '').trim();

      // "Fixed price" → keep "Fixed price from day one" on one line
      if (spanText === 'Fixed price') {
        h3.setAttribute('data-orwyx-qch3-patched', '1');
        // Ensure a single space between span text and "from day one"
        var next = mainSpan.nextSibling;
        if (next && next.nodeType === 3) {
          next.nodeValue = next.nodeValue.replace(/^\s*/, ' ');
        }
      }

      // "ownership" or "ownership," → remove commas, keep "Full ownership forever" on one line
      if (/^ownership/i.test(spanText)) {
        h3.setAttribute('data-orwyx-qch3-patched', '1');
        // Remove comma from all text nodes inside this h3 (including inside the span)
        var tw = document.createTreeWalker(h3, NodeFilter.SHOW_TEXT, null);
        var tn;
        while ((tn = tw.nextNode())) { tn.nodeValue = tn.nodeValue.replace(/,/g, ''); }
        // Small card has "Full<br><span>" — hide that <br> and insert a space so it reads "Full ownership"
        var prevNode = mainSpan.previousSibling;
        if (prevNode && prevNode.nodeName === 'BR') {
          prevNode.style.display = 'none';
          h3.insertBefore(document.createTextNode(' '), mainSpan);
        }
        // Ensure a space before "forever" text node (no <br>)
        var afterSpan = mainSpan.nextSibling;
        if (afterSpan && afterSpan.nodeType === 3 && /forever/i.test(afterSpan.nodeValue)) {
          afterSpan.nodeValue = afterSpan.nodeValue.replace(/^\s*/, ' ');
        }
      }

      // "revisions" → keep "Unlimited revisions" on one line with a space
      if (spanText === 'revisions') {
        h3.setAttribute('data-orwyx-qch3-patched', '1');
        mainSpan.style.display = 'inline';
        // Ensure space before the span (between "Unlimited" and "revisions")
        var prevRevNode = mainSpan.previousSibling;
        if (prevRevNode && prevRevNode.nodeType === 3) {
          prevRevNode.nodeValue = prevRevNode.nodeValue.replace(/\s*$/, ' ');
        } else if (!prevRevNode || prevRevNode.nodeType !== 3) {
          h3.insertBefore(document.createTextNode(' '), mainSpan);
        }
      }
    });
  }

  // =====================================
  // COMPARISON TABLE: ORWYX ROW ICON
  // =====================================
  // The iridescent-a image is the icon slot in the Orwyx row of the DIY/agency
  // comparison table. It lives inside a flex row: [img.size-14] [div.min-w-0 text].
  // We hide the img and insert the gradient "o" as a flow sibling so it sits at the
  // left of the row, vertically centered by the parent flex container.
  function patchComparisonIcon() {
    var img = document.querySelector('img[src*="iridescent-a"]');
    if (!img || img.getAttribute('data-orwyx-comp-patched')) return;
    img.setAttribute('data-orwyx-comp-patched', '1');
    hideNode(img);
    img.style.display = 'none';
    if (img.parentElement.querySelector('[data-orwyx-comp-icon]')) return;
    var icon = document.createElement('span');
    icon.setAttribute('data-orwyx-comp-icon', '1');
    icon.style.cssText =
      'display:inline-flex;flex-shrink:0;align-items:center;justify-content:center;' +
      'width:3.5rem;height:3.5rem;';
    icon.innerHTML =
      '<span style="font-weight:700;line-height:1;font-size:2.8rem;' +
      'background:' + AURORA + ';-webkit-background-clip:text;background-clip:text;color:transparent">o</span>';
    img.parentElement.insertBefore(icon, img);
  }

  // =====================================
  // BEAUTY CARD: DISABLE CLICK (no modal)
  // =====================================
  function disableBeautyCardClick() {
    // Find the clickable group ancestor of the beauty card by looking for the
    // element that contains "Grechow's Beauty" text and has a click handler.
    var allGroups = document.querySelectorAll('.group');
    for (var i = 0; i < allGroups.length; i++) {
      var g = allGroups[i];
      if (g.getAttribute('data-orwyx-click-disabled')) continue;
      if (g.textContent && g.textContent.indexOf("Grechow") >= 0) {
        g.setAttribute('data-orwyx-click-disabled', '1');
        g.addEventListener('click', function(e) {
          e.stopImmediatePropagation();
          e.preventDefault();
        }, true);
        g.style.cursor = 'default';
        break;
      }
    }
  }

  // =====================================
  // BEAUTY CARD: EMBED YOUTUBE SHORTS VIDEO
  // =====================================
  function patchBeautyCard() {
    var logoImg = document.querySelector(
      'img[src*="grechows-beauty-hero"], img[alt*="Grechow\'s Beauty —"]'
    );
    if (!logoImg) return;
    var card = logoImg.closest('.flex-1.bg-white');
    if (!card || card.getAttribute('data-orwyx-beauty-patched')) return;
    card.setAttribute('data-orwyx-beauty-patched', '1');

    // Hide the existing logo tile and hero image layers in place
    Array.from(card.children).forEach(function(el) { hideNode(el); });

    // Narrow the video frame to exact 9:16 (overrides Tailwind lg:w-5/12)
    var cardInner2 = card.parentElement;
    var frame2     = cardInner2.parentElement;
    frame2.style.width     = '345px';
    frame2.style.maxWidth  = '345px';
    frame2.style.height    = '562px';
    frame2.style.flexShrink = '0';

    var embedWrap = document.createElement('div');
    embedWrap.style.cssText = 'position:absolute;inset:0;overflow:hidden;';

    var iframe = document.createElement('iframe');
    iframe.setAttribute('src',
      'https://www.youtube-nocookie.com/embed/5nGLImpNjQI' +
      '?controls=0&rel=0&modestbranding=1&iv_load_policy=3&showinfo=0&fs=0&playsinline=1&disablekb=1');
    iframe.setAttribute('allow', 'autoplay; encrypted-media');
    iframe.setAttribute('allowfullscreen', '');
    iframe.setAttribute('frameborder', '0');
    // Shift up 52px to clip the YouTube player's top chrome (CC/volume bar) above the frame edge
    iframe.style.cssText = 'position:absolute;top:-52px;left:0;right:0;width:100%;height:calc(100% + 52px);border:0;';
    embedWrap.appendChild(iframe);

    // Small patch over the YouTube logo in the bottom-right of the control bar
    var ytLogoCover = document.createElement('div');
    ytLogoCover.style.cssText =
      'position:absolute;bottom:0;right:0;width:90px;height:48px;' +
      'background:#000;z-index:2;pointer-events:none;';
    embedWrap.appendChild(ytLogoCover);

    card.appendChild(embedWrap);
  }

  // =====================================
  // INITIALIZATION
  // =====================================
  function start() {
    installHardNav();
    injectMarqueeStyles();
    runSections();
    swapImages();
    swapBrandLogos();
    swapOrbitIcon();
    swapContactWidget();
    fixLinks();
    syncSeoSchemaWithBrand();
    injectLegal();
    patchStatsCard();
    patchFeastVideo();
    patchBeautyCard();
    disableBeautyCardClick();
    patchWorkGrid();
    patchPricingHero();
    patchPricingCards();
    patchPricingTitle();
    patchAboutPage();
    patchQualityCards();
    patchComparisonIcon();
    patchCtaButtons();
    observer = new MutationObserver(function() {
      schedule();
    });
    observeRoot();
    fixHashScroll();
    [80, 300, 800, 1600, 3000, 5000, 8000].forEach(function(t) {
      setTimeout(function() {
        if (observer) observer.disconnect();
        runSections();
        swapImages();
        swapBrandLogos();
        swapOrbitIcon();
        swapContactWidget();
        fixLinks();
        syncSeoSchemaWithBrand();
        injectLegal();
        patchStatsCard();
        patchFeastVideo();
        patchBeautyCard();
        disableBeautyCardClick();
        patchWorkGrid();
        patchPricingHero();
        patchPricingCards();
        patchPricingTitle();
        patchAboutPage();
        patchServicesPage();
        patchQualityCards();
        patchComparisonIcon();
        patchCtaButtons();
        observeRoot();
      }, t);
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
