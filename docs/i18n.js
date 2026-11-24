// Motore delle lingue del sito. Ogni pagina carica prima i suoi testi (testi-*.js, che riempiono window.UNICUM_TEXT)
// e poi questo file. L'italiano è il testo scritto nell'HTML; gli elementi con data-t="chiave" ricevono la traduzione
// (HTML ammesso), data-t-alt e data-t-aria traducono alt e aria-label, 'meta.title' e 'meta.desc' titolo e descrizione.
// Lingua: ?lang=xx (ricordata per la visita, così vale anche passando da una pagina all'altra), poi la scelta salvata
// con i bottoni [data-lang], poi la lingua del telefono/browser; altrimenti inglese.
(function () {
  var D = window.UNICUM_TEXT || {};
  var SUPPORTED = ['it', 'en', 'es', 'fr', 'de', 'pt'];
  var KEY = 'unicum-lang';
  var nodes = [].slice.call(document.querySelectorAll('[data-t],[data-t-alt],[data-t-aria]'));
  // Il testo italiano originale di ogni elemento, per poter tornare all'italiano senza ricaricare
  var orig = nodes.map(function (n) {
    return { html: n.innerHTML, alt: n.getAttribute('alt'), aria: n.getAttribute('aria-label') };
  });
  var meta = document.querySelector('meta[name="description"]');
  var origTitle = document.title, origDesc = meta ? meta.content : '';
  var buttons = [].slice.call(document.querySelectorAll('[data-lang]'));

  function ok(l) { return l && SUPPORTED.indexOf(l) >= 0; }

  function pick() {
    try {
      var q = (new URLSearchParams(location.search).get('lang') || '').toLowerCase();
      if (ok(q)) { sessionStorage.setItem(KEY, q); return q; }
      var saved = localStorage.getItem(KEY);
      if (ok(saved)) return saved;
      var visit = sessionStorage.getItem(KEY);
      if (ok(visit)) return visit;
    } catch (e) {}
    var list = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || ''];
    for (var i = 0; i < list.length; i++) {
      var c = String(list[i] || '').slice(0, 2).toLowerCase();
      if (ok(c)) return c;
    }
    return 'en';
  }

  function apply(lang) {
    var t = lang === 'it' ? {} : (D[lang] || {});
    nodes.forEach(function (n, i) {
      var o = orig[i], k;
      if ((k = n.getAttribute('data-t'))) n.innerHTML = t[k] != null ? t[k] : o.html;
      if ((k = n.getAttribute('data-t-alt'))) n.setAttribute('alt', t[k] != null ? t[k] : o.alt);
      if ((k = n.getAttribute('data-t-aria'))) n.setAttribute('aria-label', t[k] != null ? t[k] : o.aria);
    });
    document.title = t['meta.title'] || origTitle;
    if (meta) meta.content = t['meta.desc'] || origDesc;
    document.documentElement.lang = lang;
    buttons.forEach(function (b) { b.setAttribute('aria-pressed', b.getAttribute('data-lang') === lang ? 'true' : 'false'); });
  }

  buttons.forEach(function (b) {
    b.addEventListener('click', function () {
      var l = b.getAttribute('data-lang');
      try { localStorage.setItem(KEY, l); sessionStorage.setItem(KEY, l); } catch (e) {}
      apply(l);
    });
  });

  apply(pick());
})();
