(function(token, options = {}) {
  const textOffset = options.textOffset ?? 0;
  const maxChars = options.maxChars ?? 12000;
  if (!Number.isInteger(textOffset) || textOffset < 0 || textOffset > 500000 ||
      !Number.isInteger(maxChars) || maxChars < 1000 || maxChars > 24000)
    throw Error('网页读取范围无效');
  const visible = el => {
    if (!(el instanceof HTMLElement) || el.closest('[hidden],[inert],[aria-hidden="true"]')) return false;
    const rect = el.getBoundingClientRect();
    if (!rect.width || !rect.height) return false;
    for (let parent = el; parent; parent = parent.parentElement) {
      const style = getComputedStyle(parent);
      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
    }
    return true;
  };
  const inViewport = el => {
    const rect = el.getBoundingClientRect();
    return rect.bottom > 0 && rect.top < innerHeight && rect.right > 0 && rect.left < innerWidth;
  };
  const disabled = el => el.matches(':disabled') || el.closest('[aria-disabled="true"]') !== null;
  const labelText = label => {
    const copy = label.cloneNode(true);
    copy.querySelectorAll('input,textarea,select,[contenteditable]').forEach(field => field.remove());
    return copy.textContent;
  };
  const name = el => (el.getAttribute('aria-label') ||
    (el.getAttribute('aria-labelledby') || '').split(/\s+/).map(id => document.getElementById(id)?.textContent || '').join(' ').trim() ||
    (el.labels ? Array.from(el.labels).map(labelText).join(' ') : '') || el.getAttribute('placeholder') ||
    el.innerText || el.getAttribute('title') || el.getAttribute('alt') || '').trim().slice(0, 180);
  const sensitive = el => /password|email|tel|hidden/.test(el.type || '') ||
    /password|credit|card|cc-|token|secret|api.?key|one-time|otp|email|phone|(?:^|[-_])pin(?:$|[-_])/i.test([el.autocomplete, el.name, el.id].join(' '));
  const signature = el => JSON.stringify([el.tagName, el.id, el.getAttribute('name'), el.type,
    el.getAttribute('role'), name(el), el instanceof HTMLAnchorElement ? el.href : null,
    el.getAttribute('contenteditable'), el instanceof HTMLInputElement ? el.form?.id : null]);
  const all = Array.from(document.querySelectorAll('a[href],button,input:not([type=hidden]),textarea,select,[role=button],[role=link],[role=region],[role=list],[role=grid],[role=dialog],[contenteditable=true],[contenteditable=""]'))
    .filter(visible).filter(el => !options.viewportOnly || inViewport(el));
  const priority = el => (el.closest('dialog[open],[aria-modal=true]') ? 2 : 0) + (inViewport(el) ? 1 : 0);
  const ranked = all.map(el => ({el, priority: priority(el)})).sort((a,b) => b.priority-a.priority).slice(0, 100).map(x => x.el);
  const elements = new Map();
  const items = ranked.map((el, index) => {
    const id = index + 1;
    elements.set(id, {node: el, signature: signature(el)});
    const item = {id, role: el.getAttribute('role') || el.tagName.toLowerCase(), name: name(el),
      disabled: disabled(el), inViewport: inViewport(el)};
    if (el instanceof HTMLAnchorElement && /^https?:/.test(el.href)) {
      const url = new URL(el.href); url.username = ''; url.password = ''; item.href = url.href;
    }
    if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement) {
      item.kind = el.type;
      item.masked = sensitive(el);
      if (el.type === 'checkbox' || el.type === 'radio') item.checked = el.checked;
      else item.value = item.masked ? '[masked]' : el.value.slice(0, 120);
      if (el instanceof HTMLSelectElement && !item.masked) {
        item.options = Array.from(el.options).slice(0, 100).map(option => ({value: option.value.slice(0, 512),
          label: option.label.slice(0, 180), selected: option.selected,
          disabled: option.disabled || option.parentElement?.disabled === true}));
        item.optionsTruncated = el.options.length > 100;
        item.multiple = el.multiple;
      }
    }
    if (el.isContentEditable) { item.kind = 'contenteditable'; item.masked = sensitive(el); }
    if (el.scrollHeight > el.clientHeight + 1 || el.scrollWidth > el.clientWidth + 1)
      item.scrollable = {x: el.scrollLeft, y: el.scrollTop, width: el.clientWidth, height: el.clientHeight,
        scrollWidth: el.scrollWidth, scrollHeight: el.scrollHeight};
    return item;
  });
  let root = document.querySelector('main,article,[role=main]') || document.body;
  if (options.region != null) {
    if (typeof options.region !== 'string' || !options.region.trim() || options.region.length > 512) throw Error('网页区域选择器无效');
    root = document.querySelector(options.region);
    if (!root || !visible(root)) throw Error('未找到可见网页区域');
  }
  // Stop after the requested page plus one character; large documents remain pageable.
  const end = textOffset + maxChars;
  let source = '', examined = 0, exhausted = true;
  if (root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {acceptNode(node) {
      const el = node.parentElement;
      return el && visible(el) && (!options.viewportOnly || inViewport(el)) &&
        !el.closest('script,style,noscript,input,textarea,select,[contenteditable]') ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
    }});
    while (walker.nextNode()) {
      if (++examined > 20000) { exhausted = false; break; }
      const text = walker.currentNode.textContent.replace(/\s+/g, ' ').trim();
      if (text) source += (source ? '\n' : '') + text.slice(0, Math.max(0, end + 1 - source.length));
      if (source.length > end) { exhausted = false; break; }
    }
  }
  const text = source.slice(textOffset, end);
  const hasMore = source.length > end;
  window.__xuanjiBrowser = {token, url: location.href, elements, signature, sensitive};
  const scroller = document.scrollingElement || document.documentElement;
  return {snapshotId: token, url: location.href, title: document.title.slice(0,200), ready: document.readyState,
    untrustedContent: true, text, items,
    page: {textOffset, maxChars, nextOffset: hasMore ? end : null, hasMore, scanLimited: !exhausted && !hasMore},
    viewport: {width: innerWidth, height: innerHeight, scrollX, scrollY,
      scrollWidth: scroller.scrollWidth, scrollHeight: scroller.scrollHeight},
    limitations: ['Frame contents are not included; use the original page URL when available.',
      'Shadow DOM contents are not included.', 'Sensitive fields and file upload require manual interaction.'],
    frames: document.querySelectorAll('iframe,frame').length,
    truncated: {text: hasMore || !exhausted, elements: all.length > 100}};
})
