// The index page's tables: each `.pivot[data-pivot]` element groups the declarations in its
// `[data-p="data"]` JSON by module or by Construct, with the measure that `data-pivot` names.
(() => {
  const STATE_LABELS = { full: "Fully covered", partial: "Partly covered", none: "No expected test runs it", unassessed: "Not expected to be tested" };
  const MUTANT_LABELS = { survived: "Has survived mutants", uncovered: "Has uncovered mutants", killed: "Every mutant killed" };
  const mutants = (n) => `${n} ${n === 1 ? "mutant" : "mutants"}`;
  const pct = (x, total) => (total ? Math.round((100 * x) / total) : 0) + "%";
  const tile = (cls, value, label) => `<div class="tile ${cls}"><div class="value">${value}</div><div class="label">${label}</div></div>`;

  // Each measure: its facet, its counts, the cells after Decls, and the tiles above the table.
  const MEASURES = {
    coverage: {
      noun: "declarations",
      facet: { id: "state", name: "Coverage", value: (r) => [r.coverageState], label: (v) => STATE_LABELS[v], order: Object.keys(STATE_LABELS) },
      derive(o) {
        o.assessed = o.covered + o.incidental + o.uncovered;
        o.coverageState = o.assessed === 0 ? "unassessed" : o.covered === o.assessed ? "full" : o.covered === 0 ? "none" : "partial";
        o.faded = o.assessed === 0;
      },
      zero: () => ({ covered: 0, incidental: 0, uncovered: 0, run: 0, notRun: 0 }),
      add(t, r) { t.covered += r.covered; t.incidental += r.incidental; t.uncovered += r.uncovered; t.run += r.run; t.notRun += r.notRun; },
      // Percentages compare by share of assessed lines; a row with no assessed lines sorts last in either direction.
      sortValue(t, key) {
        const a = t.covered + t.incidental + t.uncovered;
        return key === "size" ? a || t.run + t.notRun : a ? t[key] / a : null;
      },
      cells(t) {
        const a = t.covered + t.incidental + t.uncovered;
        const v = (x) => `<span class="v">${x}</span>`;
        if (a === 0) {
          const tot = t.run + t.notRun;
          const w = (x) => (tot ? (100 * x) / tot : 0).toFixed(1);
          return `<td class="n num">${v(tot)}</td><td class="barcol"><span class="bar" title="Not expected to be tested: ${t.run} of ${tot} lines ran"><i class="r" style="width:${w(t.run)}%"></i></span></td><td class="n num muted" colspan="3">${v("not assessed")}</td>`;
        }
        const p = (x) => Math.round((100 * x) / a), w = (x) => ((100 * x) / a).toFixed(1);
        return `<td class="n num">${v(a)}</td><td class="barcol"><span class="bar" title="${p(t.covered)}% covered, ${p(t.incidental)}% incidental, ${p(t.uncovered)}% uncovered of ${a} lines"><i class="c" style="width:${w(t.covered)}%"></i><i class="i" style="width:${w(t.incidental)}%"></i><i class="u" style="width:${w(t.uncovered)}%"></i></span></td>` +
          `<td class="n num c">${v(p(t.covered) + "%")}</td><td class="n num i">${v(p(t.incidental) + "%")}</td><td class="n num u">${v(p(t.uncovered) + "%")}</td>`;
      },
      tiles(t) {
        const a = t.covered + t.incidental + t.uncovered;
        return tile("c", pct(t.covered, a), `Covered · ${t.covered} lines`) + tile("i", pct(t.incidental, a), `Incidental · ${t.incidental} lines`) +
          tile("u", pct(t.uncovered, a), `Uncovered · ${t.uncovered} lines`);
      },
    },
    mutation: {
      noun: "declarations with mutants",
      facet: { id: "mutants", name: "Mutants", value: (r) => r.mutantStates, label: (v) => MUTANT_LABELS[v], order: Object.keys(MUTANT_LABELS) },
      derive(o) {
        o.mutantStates = o.survived + o.uncovered === 0 ? ["killed"] : [].concat(o.survived ? ["survived"] : [], o.uncovered ? ["uncovered"] : []);
        o.faded = false;
      },
      zero: () => ({ killed: 0, survived: 0, uncovered: 0 }),
      add(t, r) { t.killed += r.killed; t.survived += r.survived; t.uncovered += r.uncovered; },
      sortValue(t, key) {
        const m = t.killed + t.survived + t.uncovered;
        return key === "size" ? m : m ? t[key] / m : null;
      },
      cells(t) {
        const m = t.killed + t.survived + t.uncovered;
        const v = (x) => `<span class="v">${x}</span>`;
        const p = (x) => Math.round((100 * x) / m), w = (x) => ((100 * x) / m).toFixed(1);
        return `<td class="n num">${v(m)}</td><td class="barcol"><span class="bar" title="${t.killed} killed, ${t.uncovered} uncovered, ${t.survived} survived of ${m} mutants"><i class="c" style="width:${w(t.killed)}%"></i><i class="i" style="width:${w(t.uncovered)}%"></i><i class="u" style="width:${w(t.survived)}%"></i></span></td>` +
          `<td class="n num c" title="${t.killed} mutants">${v(p(t.killed) + "%")}</td><td class="n num i" title="${t.uncovered} mutants">${v(p(t.uncovered) + "%")}</td><td class="n num u" title="${t.survived} mutants">${v(p(t.survived) + "%")}</td>`;
      },
      tiles(t) {
        const m = t.killed + t.survived + t.uncovered, run = t.killed + t.survived;
        return tile("c", pct(t.killed, m), `Killed · ${mutants(t.killed)}`) + tile("i", pct(t.uncovered, m), `Uncovered · ${mutants(t.uncovered)}`) +
          tile("u", pct(t.survived, m), `Survived · ${mutants(t.survived)}`) + tile("", run ? pct(t.killed, run) : "–", "Score · killed of the mutants a test runs");
      },
    },
  };

  document.querySelectorAll(".pivot[data-pivot]").forEach((root) => createPivot(root, MEASURES[root.dataset.pivot], root.dataset.pivot === "coverage"));

  function createPivot(root, measure, useHash) {
  const VIEWS = {
    modules: { inner: [["", "Flat"], ["construct", "Construct"], ["file", "File"]], opts: ["affinity", "nested"] },
    constructs: { inner: [["", "Flat"], ["feature", "Module"]], opts: ["affinity"] },
  };
  const OPT_LABELS = { affinity: "Affinity", nested: "Nested packages" };
  const MODULE_DIMS = new Set(["area", "feature", "module"]);

  // Every option starts included; unticking one excludes it. A declaration passes a facet when any of its values is included.
  const FACETS = [
    measure.facet,
    { id: "expected", name: "Expected test category", value: (r) => (Array.isArray(r.expected) ? r.expected : [r.expected]),
      label: (v) => (v === "none" ? "Not expected" : v === "unspecified" ? "No expectation" : categories[v] || v) },
    { id: "construct", name: "Construct", value: (r) => [r.construct], parent: (r) => r.layer, label: (v) => (v.includes(".") ? v.split(".").slice(1).join(".") : v) },
    { id: "moduleType", name: "Module type", value: (r) => [r.area.startsWith(":feature") ? ":feature" : r.area], mono: true },
    { id: "affinity", name: "Affinity", value: (r) => [r.affinity], mono: true },
    { id: "sourceSet", name: "Source set", value: (r) => [r.sourceSet], mono: true },
  ];

  const state = {
    view: "modules", inner: { modules: "file", constructs: "" }, affinity: true, nested: true,
    search: "", sort: { key: "name", dir: 1 }, shortPkg: false, qualified: true, collapseFiles: true,
    open: new Set(), facetOpen: new Set(), excluded: Object.fromEntries(FACETS.map((f) => [f.id, new Set()])),
  };
  let rows = [], categories = {}, lastTree = [], facetValues = {};
  const $ = (id) => root.querySelector(`[data-p="${id}"]`);
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  const splitBy = (list, keyOf) => { const m = new Map(); list.forEach((r) => { const k = keyOf(r); if (!m.has(k)) m.set(k, []); m.get(k).push(r); }); return m; };

  function derive(f, r) {
    const o = {}; f.forEach((k, i) => (o[k] = r[i]));
    o.fullPackage = o.package;
    o.pkgSegs = o.fullPackage.split(".").filter(Boolean);
    o.fileName = o.path.split("/").pop();
    o.sourceSet = (o.path.match(/\/src\/([^/]+)\//) || [, "?"])[1];
    o.construct = o.construct || (o.basis === "module" ? "Module expectation" : o.basis === "noexpectation" ? "No expectation" : "Unclassified");
    o.constructShort = o.construct.includes(".") ? o.construct.split(".").slice(1).join(".") : o.construct;
    measure.derive(o);
    o.haystack = [o.package, o.short, o.path, o.module, o.construct].join(" ").toLowerCase();
    return o;
  }
  const fileId = (r) => [r.fullPackage, r.fileName].filter(Boolean).join(".");
  const relativeTo = (full, base) => (base && full.startsWith(base + ".") ? full.slice(base.length + 1) : full);

  function passesFacet(r, f) { const ex = state.excluded[f.id]; return !ex.size || f.value(r).some((v) => !ex.has(v)); }
  function matchesFacets(r, except) { return FACETS.every((f) => f.id === except || passesFacet(r, f)); }
  function matchesSearch(r) { const q = state.search.trim().toLowerCase(); return !q || q.split(/\s+/).every((t) => r.haystack.includes(t)); }
  const filtered = () => rows.filter((r) => matchesSearch(r) && matchesFacets(r));

  const zero = () => ({ ...measure.zero(), decls: 0 });
  const add = (t, r) => { measure.add(t, r); t.decls += 1; };
  const tallyOf = (list) => { const t = zero(); list.forEach((r) => add(t, r)); return t; };
  const leaves = (list, id) => sortNodes(list.map((r) => ({ leaf: true, r, id: id + "|" + r.path + "#" + r.line })));

  function levels() {
    const inner = state.inner[state.view];
    if (state.view === "modules") return ["area", "feature"].concat(state.affinity ? ["module"] : [], ["pkg"], inner ? [inner] : []);
    return ["layer", "construct"].concat(inner === "feature" ? (state.affinity ? ["feature", "module"] : ["feature"]) : []);
  }

  function build(list, lv, li, id, parentModule) {
    if (li >= lv.length) return leaves(list, id);
    const dim = lv[li];
    if (dim === "pkg") return state.nested ? packageTops(list, [], lv, li, id, parentModule) : flatPackages(list, lv, li, id, parentModule);
    if (MODULE_DIMS.has(dim) && list.every((r) => r[dim] === parentModule)) return build(list, lv, li + 1, id, parentModule);
    const keyOf = dim === "file" ? (r) => r.fileName : (r) => r[dim];
    const nodes = [...splitBy(list, keyOf)].map(([key, members]) => {
      let label = String(key);
      if (MODULE_DIMS.has(dim) && parentModule && label.startsWith(parentModule + ":")) label = label.slice(parentModule.length);
      if (dim === "construct") label = label.includes(".") ? label.split(".").slice(1).join(".") : label;
      const nid = id + "|" + dim + "=" + key;
      return { key, members, tally: tallyOf(members), label, mono: MODULE_DIMS.has(dim) || dim === "file", title: String(key), dim, id: nid,
        children: build(members, lv, li + 1, nid, MODULE_DIMS.has(dim) ? String(key) : parentModule) };
    });
    if (dim === "file" && state.collapseFiles) return sortNodes(nodes.map((n) => (n.members.length === 1 ? { leaf: true, r: n.members[0], id: n.id } : n)));
    return sortNodes(nodes);
  }

  // Nested: the first package row is the deepest package every declaration below it shares, named in full.
  // Each package row holds its subpackages, then its own declarations grouped by the inner level.
  function packageTops(list, prefix, lv, li, id, parentModule) {
    const direct = list.filter((r) => r.pkgSegs.length === prefix.length);
    const groups = splitBy(list.filter((r) => r.pkgSegs.length > prefix.length), (r) => r.pkgSegs[prefix.length]);
    if (!direct.length && groups.size === 1) { const [k] = groups.keys(); return packageTops(list, prefix.concat(k), lv, li, id, parentModule); }
    if (prefix.length) return [packageNode(list, prefix, null, lv, li, id, parentModule)];
    return sortNodes([...groups].flatMap(([k, rs]) => packageTops(rs, [k], lv, li, id, parentModule))).concat(build(direct, lv, li + 1, id + "|pkg=", parentModule));
  }
  function packageNode(list, prefix, parentFull, lv, li, id, parentModule) {
    const full = prefix.join(".");
    const nid = id + "|pkg=" + full;
    const direct = list.filter((r) => r.pkgSegs.length === prefix.length);
    const subs = [...splitBy(list.filter((r) => r.pkgSegs.length > prefix.length), (r) => r.pkgSegs[prefix.length])]
      .map(([k, rs]) => packageNode(rs, prefix.concat(k), full, lv, li, id, parentModule));
    return { key: full, members: list, tally: tallyOf(list), label: parentFull ? prefix[prefix.length - 1] : full, mono: true, title: full, dim: "pkg", id: nid,
      children: sortNodes(subs).concat(build(direct, lv, li + 1, nid, parentModule)) };
  }
  // Flat: one row per package. With short names, a package below the module's shared package is named relative to it.
  function flatPackages(list, lv, li, id, parentModule) {
    let base = list[0].pkgSegs;
    list.forEach((r) => { let k = 0; while (k < base.length && base[k] === r.pkgSegs[k]) k++; base = base.slice(0, k); });
    const baseName = base.join(".");
    const baseShown = list.some((r) => r.fullPackage === baseName);
    const nodes = sortNodes([...splitBy(list, (r) => r.fullPackage)].map(([full, members]) => {
      const nid = id + "|pkg=" + full;
      const label = state.shortPkg && baseShown && full !== baseName ? relativeTo(full, baseName) : full;
      return { key: full, members, tally: tallyOf(members), label, mono: true, title: full, dim: "pkg", id: nid, children: build(members, lv, li + 1, nid, parentModule) };
    }));
    return state.shortPkg ? nodes.filter((n) => n.key === baseName).concat(nodes.filter((n) => n.key !== baseName)) : nodes;
  }

  function sortValue(n, key) {
    const t = n.leaf ? n.r : n.tally;
    switch (key) {
      case "name": return (n.leaf ? (state.qualified ? n.r.fullPackage + "." + n.r.short : n.r.short) : n.label).toLowerCase();
      case "decls": return n.leaf ? 1 : t.decls;
      default: return measure.sortValue(t, key);
    }
  }
  function sortNodes(nodes) {
    const { key, dir } = state.sort;
    return nodes.sort((x, y) => {
      const a = sortValue(x, key), b = sortValue(y, key);
      if (a === null || b === null) return a === b ? 0 : a === null ? 1 : -1;
      const c = typeof a === "string" ? a.localeCompare(b, undefined, { numeric: true }) : a - b;
      return dir * c || sortValue(x, "name").localeCompare(sortValue(y, "name"));
    });
  }

  function expectedHtml(r) {
    if (Array.isArray(r.expected)) return r.expected.map((c) => `<span class="chip">${esc(categories[c] || c)}</span>`).join("");
    return `<span class="chip none">${r.expected === "none" ? "Not expected" : "No expectation"}</span>`;
  }

  const cells = (t) => measure.cells(t);

  function leafHtml(r, depth) {
    const lv = levels();
    const tags = [];
    if (!lv.includes("construct") && r.basis !== "module") tags.push(`<span class="tag construct" title="${esc(r.construct)}">${esc(r.constructShort)}</span>`);
    tags.push(`<span class="tag" title="${esc(r.module)}">${esc(r.affinity)}</span>`);
    if (r.sourceSet !== "commonMain") tags.push(`<span class="tag">${esc(r.sourceSet)}</span>`);
    return `<tr class="leaf${r.faded ? " faded" : ""}"><td style="padding-left:${8 + depth * 18}px"><div class="name"><span class="tw-spacer"></span><div class="decl">` +
      `<div class="l1"><a class="main" href="${esc(r.link)}" title="${esc(r.fullPackage + "." + r.short)}">${esc(r.short)}</a>${tags.join("")}</div>` +
      `<div class="l2" title="${esc(r.path)}">${esc(fileId(r))}:${r.line}</div></div></div></td>` +
      `<td class="exp">${expectedHtml(r)}</td><td class="n num"></td>${cells(r)}</tr>`;
  }

  function render() {
    const list = filtered();
    $("tiles").innerHTML = measure.tiles(tallyOf(list));

    lastTree = build(list, levels(), 0, state.view, null);
    const searching = state.search.trim() !== "";
    const out = [];
    const walk = (nodes, depth) => {
      for (const n of nodes) {
        if (n.leaf) { out.push(leafHtml(n.r, depth)); continue; }
        const open = searching || state.open.has(n.id);
        const exp = n.dim === "construct" && new Set(n.members.map((m) => JSON.stringify(m.expected))).size === 1 ? expectedHtml(n.members[0]) : "";
        out.push(`<tr class="group${depth === 0 ? " top" : ""}${open ? " open" : ""}" data-id="${esc(n.id)}"><td style="padding-left:${8 + depth * 18}px"><div class="name">` +
          `<button class="tw" aria-expanded="${open}" aria-label="${open ? "Collapse" : "Expand"}"><span>▶</span></button><span class="${n.mono ? "mono" : ""}" title="${esc(n.title)}">${esc(n.label)}</span>${n.dim === "file" ? `<span class="fcount">(${n.tally.decls})</span>` : ""}</div></td>` +
          `<td class="exp">${exp}</td><td class="n num"><span class="v">${n.tally.decls}</span></td>${cells(n.tally)}</tr>`);
        if (open) walk(n.children, depth + 1);
      }
    };
    walk(lastTree, 0);
    $("body").innerHTML = out.length ? out.join("") : `<tr><td colspan="8" class="empty">No declarations match. Clear the search or tick more options under Filter.</td></tr>`;
    $("status").textContent = `${list.length} of ${rows.length} ${measure.noun}`;
    renderAxes();
    renderActive();
    renderFacets();
  }

  function renderAxes() {
    $("views").querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.view === state.view)));
    $("inner").innerHTML = VIEWS[state.view].inner.map(([k, label]) => `<button data-inner="${k}" aria-pressed="${state.inner[state.view] === k}">${label}</button>`).join("");
    const inert = (k) => k === "affinity" && state.view === "constructs" && state.inner.constructs !== "feature";
    $("axisOpts").innerHTML = VIEWS[state.view].opts.map((k) =>
      `<button data-opt="${k}" aria-pressed="${state[k]}"${inert(k) ? ' disabled title="Affinity applies to the Module grouping."' : ""}><span class="box" aria-hidden="true">${state[k] ? "✓" : ""}</span>${OPT_LABELS[k]}</button>`).join("");
  }

  // ---- Filter menu
  function computeFacetValues() {
    for (const f of FACETS) {
      const parents = new Map(), set = new Set();
      rows.forEach((r) => f.value(r).forEach((v) => { set.add(v); if (f.parent) parents.set(v, f.parent(r)); }));
      const label = (v) => String(f.label ? f.label(v) : v);
      const values = [...set].sort(f.order ? (a, b) => f.order.indexOf(a) - f.order.indexOf(b) : (a, b) => label(a).localeCompare(label(b)));
      facetValues[f.id] = { values, parents };
    }
  }
  const checkbox = (attrs, included, total) => `<input type="checkbox" ${attrs} ${included === total ? "checked" : ""} data-indeterminate="${included > 0 && included < total}">`;

  function renderFacets() {
    const focused = document.activeElement && document.activeElement.dataset ? { ...document.activeElement.dataset } : null;
    $("facets").innerHTML = FACETS.map((f) => {
      const { values, parents } = facetValues[f.id];
      const ex = state.excluded[f.id];
      const counts = new Map();
      rows.filter((r) => matchesSearch(r) && matchesFacets(r, f.id)).forEach((r) => f.value(r).forEach((v) => counts.set(v, (counts.get(v) || 0) + 1)));
      const open = state.facetOpen.has(f.id);
      const included = values.filter((v) => !ex.has(v)).length;
      const option = (v, deep) => `<div class="frow leafopt${deep ? " deep" : ""}"><label><input type="checkbox" data-facet="${f.id}" data-value="${esc(v)}" ${ex.has(v) ? "" : "checked"}>` +
        `<span class="k${f.mono ? " mono" : ""}">${esc(f.label ? f.label(v) : v)}</span><span class="num">${counts.get(v) || 0}</span></label></div>`;
      let body = "";
      if (open && f.parent) {
        const byParent = new Map();
        values.forEach((v) => { const p = parents.get(v); if (!byParent.has(p)) byParent.set(p, []); byParent.get(p).push(v); });
        body = [...byParent].sort(([a], [b]) => a.localeCompare(b)).map(([p, vs]) => {
          const key = f.id + ">" + p, sub = state.facetOpen.has(key);
          const inc = vs.filter((v) => !ex.has(v)).length;
          const n = vs.reduce((s, v) => s + (counts.get(v) || 0), 0);
          return `<div class="frow sub"><button class="chev" data-toggle="${esc(key)}" aria-expanded="${sub}" aria-label="Show ${esc(p)} Constructs"><span>▶</span></button>` +
            `<label>${checkbox(`data-facet="${f.id}" data-parent="${esc(p)}"`, inc, vs.length)}<span class="k">${esc(p)}</span><span class="num">${n}</span></label></div>` +
            (sub ? vs.map((v) => option(v, true)).join("") : "");
        }).join("");
      } else if (open) body = values.map((v) => option(v)).join("");
      const summary = included === values.length ? "all" : `${included} of ${values.length}`;
      return `<div class="facet"><div class="frow head"><button class="chev" data-toggle="${f.id}" aria-expanded="${open}" aria-label="Show ${esc(f.name)} options"><span>▶</span></button>` +
        `<label>${checkbox(`data-facet="${f.id}" data-all="1"`, included, values.length)}<span class="k">${esc(f.name)}</span><span class="num">${summary}</span></label></div>${body}</div>`;
    }).join("");
    $("facets").querySelectorAll('input[data-indeterminate="true"]').forEach((b) => (b.indeterminate = true));
    if (focused && (focused.facet || focused.toggle)) {
      const sel = focused.toggle ? `[data-toggle="${CSS.escape(focused.toggle)}"]`
        : `input[data-facet="${CSS.escape(focused.facet)}"]` + (focused.value ? `[data-value="${CSS.escape(focused.value)}"]` : focused.parent ? `[data-parent="${CSS.escape(focused.parent)}"]` : "[data-all]");
      const el = $("facets").querySelector(sel); if (el) el.focus({ preventScroll: true });
    }
    const total = FACETS.filter((f) => state.excluded[f.id].size).length;
    $("filterCount").hidden = total === 0; $("filterCount").textContent = total;
  }

  function renderActive() {
    const chips = FACETS.filter((f) => state.excluded[f.id].size).map((f) => {
      const { values } = facetValues[f.id];
      const ex = state.excluded[f.id];
      const name = (v) => String(f.label ? f.label(v) : v);
      const inc = values.filter((v) => !ex.has(v));
      const list = (vs) => (vs.length > 3 ? vs.slice(0, 2).map(name).join(", ") + ` +${vs.length - 2}` : vs.map(name).join(", "));
      const text = inc.length === 0 ? "none" : inc.length <= ex.size ? "only " + list(inc) : "not " + list([...ex]);
      return `<span class="fchip"><b>${esc(f.name)}:</b> ${esc(text)}<button data-clear="${f.id}" aria-label="Remove the ${esc(f.name)} filter">×</button></span>`;
    });
    $("active").innerHTML = chips.length ? chips.join("") + (chips.length > 1 ? `<button class="link" data-clear="*">Clear all</button>` : "") : "";
  }

  $("facets").addEventListener("change", (e) => {
    const box = e.target; if (!box.dataset.facet) return;
    const f = FACETS.find((x) => x.id === box.dataset.facet), ex = state.excluded[f.id];
    const { values, parents } = facetValues[f.id];
    const targets = box.dataset.all ? values : box.dataset.parent !== undefined ? values.filter((v) => parents.get(v) === box.dataset.parent) : [box.dataset.value];
    targets.forEach((v) => (box.checked ? ex.delete(v) : ex.add(v)));
    render();
  });
  $("facets").addEventListener("click", (e) => {
    const t = e.target.closest("[data-toggle]"); if (!t) return;
    e.preventDefault();
    const k = t.dataset.toggle;
    state.facetOpen.has(k) ? state.facetOpen.delete(k) : state.facetOpen.add(k);
    renderFacets();
  });
  document.addEventListener("click", (e) => {
    const b = root.contains(e.target) && e.target.closest("[data-clear]");
    if (b) { e.preventDefault(); (b.dataset.clear === "*" ? FACETS.map((f) => f.id) : [b.dataset.clear]).forEach((id) => state.excluded[id].clear()); render(); return; }
    const path = e.composedPath();
    ["filterMenu", "displayMenu", "keyMenu"].forEach((id) => { const m = $(id); if (m.open && !path.includes(m)) m.open = false; });
  });

  // The key sits in the table header, whose scrolling wrapper would clip an absolutely positioned panel.
  function placeKey() {
    const menu = $("keyMenu"); if (!menu.open) return;
    const at = menu.querySelector("summary").getBoundingClientRect(), panel = menu.querySelector(".panel");
    panel.style.top = at.bottom + 6 + "px";
    panel.style.left = Math.max(16, Math.min(at.left, innerWidth - panel.offsetWidth - 16)) + "px";
  }
  $("keyMenu").addEventListener("toggle", placeKey);
  window.addEventListener("scroll", () => { $("keyMenu").open = false; }, { capture: true, passive: true });
  window.addEventListener("resize", placeKey);

  // ---- Search
  function setSearchOpen(open) {
    $("searchWrap").classList.toggle("open", open);
    $("searchBtn").setAttribute("aria-expanded", String(open));
    $("search").tabIndex = open ? 0 : -1;
    if (open) $("search").focus();
  }
  $("searchBtn").addEventListener("click", () => setSearchOpen(!$("searchWrap").classList.contains("open") || false));
  $("search").addEventListener("keydown", (e) => { if (e.key === "Escape") { e.target.value = ""; state.search = ""; render(); setSearchOpen(false); $("searchBtn").focus(); } });
  $("search").addEventListener("blur", (e) => { if (!e.target.value) setSearchOpen(false); });
  let timer;
  $("search").addEventListener("input", (e) => { clearTimeout(timer); timer = setTimeout(() => { state.search = e.target.value; render(); }, 120); });

  // ---- Axes, display, tree
  function setView(v) { if (!VIEWS[v]) return; state.view = v; render(); }
  $("views").querySelectorAll("button").forEach((b) => b.addEventListener("click", () => {
    if (!useHash || location.hash === "#" + b.dataset.view) setView(b.dataset.view); else location.hash = b.dataset.view;
  }));
  if (useHash) window.addEventListener("hashchange", () => setView(location.hash.slice(1)));
  $("inner").addEventListener("click", (e) => { const b = e.target.closest("[data-inner]"); if (!b) return; state.inner[state.view] = b.dataset.inner; render(); });
  $("axisOpts").addEventListener("click", (e) => { const b = e.target.closest("[data-opt]"); if (!b) return; state[b.dataset.opt] = !state[b.dataset.opt]; render(); });
  $("optCollapseFiles").addEventListener("change", (e) => { state.collapseFiles = e.target.checked; render(); });
  $("optShortPkg").addEventListener("change", (e) => { state.shortPkg = e.target.checked; render(); });
  $("optQualified").addEventListener("change", (e) => { state.qualified = e.target.checked; render(); });
  $("body").addEventListener("click", (e) => {
    const tr = e.target.closest("tr.group"); if (!tr) return;
    const id = tr.dataset.id;
    state.open.has(id) ? state.open.delete(id) : state.open.add(id);
    render();
    const again = $("body").querySelector(`tr.group[data-id="${CSS.escape(id)}"] .tw`); if (again) again.focus({ preventScroll: true });
  });

  function renderHeads() {
    $("heads").querySelectorAll("th[data-sort]").forEach((th) => {
      const k = th.dataset.sort, on = state.sort.key === k;
      const label = th.dataset.label || (th.dataset.label = th.textContent);
      if (on) th.setAttribute("aria-sort", state.sort.dir > 0 ? "ascending" : "descending"); else th.removeAttribute("aria-sort");
      th.innerHTML = `<button data-sortkey="${k}">${esc(label)}<span class="arrow">${on ? (state.sort.dir > 0 ? "▲" : "▼") : ""}</span></button>`;
    });
  }
  $("heads").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-sortkey]"); if (!b) return;
    const k = b.dataset.sortkey;
    state.sort = state.sort.key === k ? { key: k, dir: -state.sort.dir } : { key: k, dir: k === "name" ? 1 : -1 };
    renderHeads(); render();
  });

  // Visible group rows with their depth, in display order.
  function visibleGroups() {
    const out = [];
    const walk = (nodes, depth) => nodes.forEach((n) => { if (n.leaf) return; out.push({ n, depth }); if (state.open.has(n.id)) walk(n.children, depth + 1); });
    walk(lastTree, 0);
    return out;
  }
  $("expandOne").addEventListener("click", () => {
    const shown = visibleGroups();
    const closed = shown.filter(({ n }) => !state.open.has(n.id));
    if (!closed.length) return;
    const depth = Math.min(...closed.map((x) => x.depth));
    closed.filter((x) => x.depth === depth).forEach(({ n }) => state.open.add(n.id));
    render();
  });
  $("collapseOne").addEventListener("click", () => {
    const opened = visibleGroups().filter(({ n }) => state.open.has(n.id));
    if (!opened.length) return;
    const depth = Math.max(...opened.map((x) => x.depth));
    opened.filter((x) => x.depth === depth).forEach(({ n }) => state.open.delete(n.id));
    render();
  });
  $("expandAll").addEventListener("click", () => {
    const walk = (nodes) => nodes.forEach((n) => { if (!n.leaf) { state.open.add(n.id); walk(n.children); } });
    walk(lastTree); render();
  });
  $("collapseAll").addEventListener("click", () => { state.open.clear(); render(); });

  const data = JSON.parse($("data").textContent);
  categories = data.categories;
  rows = data.rows.map((r) => derive(data.fields, r));
  computeFacetValues();
  const start = useHash ? location.hash.slice(1) : "";
  state.view = VIEWS[start] ? start : "modules";
  renderHeads();
  render();
  }
})();
