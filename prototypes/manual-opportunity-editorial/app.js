const app = document.querySelector('#app')
const types = [
  ['short-text', 'Texto curto'], ['long-text', 'Texto longo'], ['date', 'Data'],
  ['currency', 'Moeda'], ['link', 'Link'], ['list', 'Lista'],
]

const makeField = (name, type, value) => ({ id: crypto.randomUUID(), name, type, value })
const seed = [
  {
    id: 'demo-green', title: 'Programa Horizonte Verde', institution: 'Instituto de Energia Sustentável do Vale',
    summary: 'Apoio a projetos demonstrativos que acelerem soluções de energia limpa, eficiência e descarbonização em pequenas e médias empresas.',
    deadline: '2027-04-30', status: 'Inscrições abertas', officialUrl: 'https://example.org/edital-horizonte-verde', published: true,
    sections: [
      { id: 's-green-1', title: 'Sobre a oportunidade', fields: [makeField('Objetivo', 'long-text', 'Apoiar projetos-piloto de energia renovável, armazenamento e eficiência energética com potencial de replicação regional.') ] },
      { id: 's-green-2', title: 'Quem pode participar', fields: [makeField('Público elegível', 'list', ['Pequenas e médias empresas brasileiras', 'Consórcios com uma empresa líder', 'Instituições de pesquisa como parceiras'])] },
      { id: 's-green-3', title: 'Apoio financeiro', fields: [makeField('Valor por projeto', 'currency', '750000'), makeField('Duração prevista', 'short-text', 'Até 24 meses')] },
      { id: 's-green-4', title: 'Como participar', fields: [makeField('Etapas', 'list', ['Cadastre a proposta na plataforma demonstrativa', 'Envie o plano de trabalho', 'Aguarde a avaliação técnica'])] },
    ],
  },
  {
    id: 'demo-digital', title: 'Desafio Indústria Digital', institution: 'Fundação Demonstrativa de Tecnologia Aplicada',
    summary: 'Chamada demonstrativa para testar soluções de automação, dados e segurança digital em ambientes industriais.',
    deadline: '2027-08-18', status: 'Em breve', officialUrl: 'https://example.org/desafio-industria-digital', published: false,
    sections: [
      { id: 's-digital-1', title: 'Sobre a oportunidade', fields: [makeField('Tema', 'short-text', 'Digitalização industrial')] },
      { id: 's-digital-2', title: 'Documentos e requisitos', fields: [makeField('Preparar', 'list', ['Resumo da solução', 'Plano de validação', 'Cronograma indicativo'])] },
    ],
  },
]

const state = { opportunities: seed, screen: 'admin-list', activeId: null, editorTab: 'basics', search: '' }
const escape = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char])
const current = () => state.opportunities.find((item) => item.id === state.activeId)
const typeLabel = (type) => types.find(([value]) => value === type)?.[1] ?? 'Texto curto'
const money = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(number) : escape(value)
}
const date = (value) => {
  if (!value) return 'A definir'
  const [year, month, day] = value.split('-').map(Number)
  const parsed = new Date(Date.UTC(year, month - 1, day, 12))
  return Number.isNaN(parsed.getTime()) ? 'A definir' : new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeZone: 'UTC' }).format(parsed)
}
function safeLink(value) {
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return null
    return url.href
  } catch { return null }
}
function statusClass(item) { return item.status === 'Encerrada' ? 'closed' : item.published ? '' : 'draft' }
function updateHeader() {
  document.querySelectorAll('.nav-link').forEach((button) => button.classList.toggle('is-active',
    button.dataset.action === (state.screen.startsWith('public') || state.screen === 'catalog' ? 'go-catalog' : 'go-admin')))
}
function render() {
  updateHeader()
  if (state.screen === 'admin-list') return renderAdminList()
  if (state.screen === 'editor') return renderEditor()
  if (state.screen === 'catalog') return renderCatalog()
  if (state.screen === 'public-detail') return renderPublicDetail()
}
function renderAdminList() {
  const query = state.search.trim().toLocaleLowerCase('pt-BR')
  const items = state.opportunities.filter((item) => `${item.title} ${item.institution}`.toLocaleLowerCase('pt-BR').includes(query))
  app.innerHTML = `<div class="container admin-shell">
    <div class="page-heading"><div><p class="eyebrow">Área de administração · demonstração</p><h1>Suas oportunidades</h1><p>Organize as informações e escolha o que seus visitantes vão encontrar no catálogo.</p></div>
      <button class="button button-primary" data-action="new-opportunity"><span aria-hidden="true">＋</span> Nova oportunidade</button></div>
    <div class="list-toolbar"><label class="search-box"><span aria-hidden="true">⌕</span><span class="sr-only">Buscar oportunidade</span><input type="search" placeholder="Buscar por título ou instituição" value="${escape(state.search)}" data-search></label>
      <span class="count-label">${items.length} ${items.length === 1 ? 'oportunidade' : 'oportunidades'}</span></div>
    ${items.length ? `<div class="opportunity-list" aria-label="Oportunidades cadastradas">${items.map((item) => `<article class="opportunity-row" data-opportunity="${escape(item.id)}">
      <div><div class="status-pill ${statusClass(item)}">${item.published ? 'Publicada na demonstração' : 'Rascunho'}</div><h2>${escape(item.title || 'Sem título')}</h2><p class="institution">${escape(item.institution || 'Instituição não informada')}</p>
      <div class="row-meta"><span>Prazo: ${escape(date(item.deadline))}</span><span>Situação: ${escape(item.status || 'Não informada')}</span><span>${item.sections.length} ${item.sections.length === 1 ? 'seção' : 'seções'}</span></div></div>
      <div class="row-actions"><button class="button button-secondary" data-action="edit" data-id="${escape(item.id)}">Editar oportunidade</button></div></article>`).join('')}</div>`
      : `<div class="empty-state"><h2>Nenhuma oportunidade encontrada</h2><p>Crie uma nova oportunidade ou ajuste a busca.</p><button class="button button-primary" data-action="new-opportunity">Nova oportunidade</button></div>`}
  </div>`
}
function fieldInput(field, section, index) {
  const attrs = `data-field-id="${escape(field.id)}" data-section-id="${escape(section.id)}"`
  const valueId = `value-${field.id}`
  const val = Array.isArray(field.value) ? field.value.join('\n') : field.value ?? ''
  let input
  if (field.type === 'long-text') input = `<textarea id="${valueId}" class="custom-value multiline" ${attrs} data-key="value" rows="3" placeholder="Escreva com clareza">${escape(val)}</textarea>`
  else if (field.type === 'list') input = `<textarea id="${valueId}" class="custom-value list-input" ${attrs} data-key="value" rows="3" placeholder="Um item por linha">${escape(val)}</textarea>`
  else if (field.type === 'date') input = `<input id="${valueId}" class="custom-value" type="date" ${attrs} data-key="value" value="${escape(val)}">`
  else if (field.type === 'currency') input = `<input id="${valueId}" class="custom-value" type="number" min="0" step="0.01" inputmode="decimal" ${attrs} data-key="value" value="${escape(val)}" placeholder="0,00">`
  else if (field.type === 'link') input = `<input id="${valueId}" class="custom-value" type="url" ${attrs} data-key="value" value="${escape(val)}" placeholder="https://">`
  else input = `<input id="${valueId}" class="custom-value" type="text" ${attrs} data-key="value" value="${escape(val)}" placeholder="Digite o valor">`
  return `<article class="custom-field-row"><div class="field-editor-wrap"><label class="field-editor-label" for="name-${escape(field.id)}">Nome da informação</label><input id="name-${escape(field.id)}" class="custom-name" ${attrs} data-key="name" value="${escape(field.name)}" placeholder="Ex.: Valor máximo" aria-label="Nome do campo ${index + 1}"></div>
    <div class="field-editor-wrap"><label class="field-editor-label" for="type-${escape(field.id)}">Tipo de informação</label><select id="type-${escape(field.id)}" class="custom-type" ${attrs} data-key="type" aria-label="Tipo do campo ${index + 1}">${types.map(([type, label]) => `<option value="${type}" ${field.type === type ? 'selected' : ''}>${label}</option>`).join('')}</select></div>
    <div class="field-editor-wrap value-column"><label class="field-editor-label" for="value-${escape(field.id)}">Valor</label><div id="value-wrap-${escape(field.id)}">${input}</div>${field.type === 'list' ? '<span class="helper">Separe os itens com uma nova linha.</span>' : field.type === 'link' ? '<span class="helper">Será exibido somente se o endereço for HTTP ou HTTPS.</span>' : ''}</div>
    <div class="field-row-controls" aria-label="Ordenar ou remover campo"><button class="icon-button" title="Mover campo para cima" aria-label="Mover campo para cima" data-action="field-up" ${attrs}>↑</button><button class="icon-button" title="Mover campo para baixo" aria-label="Mover campo para baixo" data-action="field-down" ${attrs}>↓</button><button class="icon-button" title="Remover campo" aria-label="Remover campo" data-action="remove-field" ${attrs}>×</button></div></article>`
}
function renderEditor() {
  const item = current()
  if (!item) { state.screen = 'admin-list'; return renderAdminList() }
  const tabs = [['basics', 'Informações principais'], ['content', 'Conteúdo da página'], ['preview', 'Prévia pública']]
  app.innerHTML = `<div class="container editor-shell"><button class="back-link" data-action="go-admin">← Voltar para oportunidades</button>
    <div class="editor-heading"><div><p class="eyebrow">${item.published ? 'Publicada na demonstração' : 'Rascunho · alterações nesta sessão'}</p><h1>${escape(item.title || 'Nova oportunidade')}</h1><p>Preencha o essencial e organize as informações como fizer sentido para este edital.</p></div>
      <button class="button ${item.published ? 'button-secondary' : 'button-primary'}" data-action="toggle-publication" data-id="${escape(item.id)}">${item.published ? 'Retirar da publicação' : 'Publicar demonstração'}</button></div>
    <nav class="step-nav" aria-label="Etapas da edição">${tabs.map(([id, label], index) => `<button class="step-button ${state.editorTab === id ? 'is-active' : ''}" data-action="editor-tab" data-tab="${id}" aria-current="${state.editorTab === id ? 'step' : 'false'}"><span>${String(index + 1).padStart(2, '0')}</span> ${label}</button>`).join('')}</nav>
    ${state.editorTab === 'basics' ? renderBasics(item) : state.editorTab === 'content' ? renderContent(item) : renderPreviewTab(item)}
  </div>`
}
function renderBasics(item) {
  const statusOptions = ['Inscrições abertas', 'Em breve', 'Encerrada']
  return `<div class="editor-layout"><section class="editor-panel"><h2>O essencial da oportunidade</h2><p class="section-intro">Comece pelas informações que ajudam alguém a decidir se quer saber mais.</p>
    <div class="form-grid"><div class="form-field full"><label for="title">Título da oportunidade</label><input id="title" required data-main="title" value="${escape(item.title)}" placeholder="Ex.: Programa de inovação aberta"></div>
      <div class="form-field full"><label for="institution">Instituição responsável</label><input id="institution" required data-main="institution" value="${escape(item.institution)}" placeholder="Nome da instituição"></div>
      <div class="form-field full"><label for="summary">Resumo</label><textarea id="summary" data-main="summary" maxlength="500" placeholder="Explique em poucas frases para quem é e o que oferece">${escape(item.summary)}</textarea><span class="helper">Uma apresentação breve. Você pode adicionar detalhes nas seções abaixo.</span></div>
      <div class="form-field"><label for="deadline">Prazo</label><input id="deadline" type="date" data-main="deadline" value="${escape(item.deadline)}"></div>
      <div class="form-field"><label for="status">Situação</label><select id="status" data-main="status">${statusOptions.map((option) => `<option ${item.status === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>
      <div class="form-field full"><label for="official-url">URL oficial</label><input id="official-url" type="url" data-main="officialUrl" value="${escape(item.officialUrl)}" placeholder="https://instituicao.gov.br/chamada"><span class="helper">Na demonstração, use endereços de exemplo. O site oficial poderá ser cadastrado na versão definitiva.</span></div></div>
      <div class="panel-footer"><span class="helper">Você pode voltar e alterar estas informações antes de publicar.</span><button class="button button-primary" data-action="editor-tab" data-tab="content">Organizar conteúdo →</button></div>
    </section><aside class="preview-panel"><div class="preview-kicker"><span class="eyebrow">Prévia atual</span><button class="button button-quiet" data-action="editor-tab" data-tab="preview">Ver página completa</button></div><div class="preview-frame">${renderEditorial(item, true)}</div></aside></div>`
}
function renderContent(item) {
  return `<section class="content-panel"><div class="page-heading"><div><h2>Conteúdo da página</h2><p class="section-intro">Crie as seções necessárias para esta oportunidade. A ordem definida aqui será a ordem da página pública.</p></div><button class="button button-secondary" data-action="add-section">＋ Adicionar seção</button></div>
    ${item.sections.length ? `<div class="section-stack">${item.sections.map((section, sectionIndex) => `<article class="section-editor" data-section="${escape(section.id)}"><header class="section-editor__head">
      <label class="sr-only" for="section-title-${escape(section.id)}">Título da seção ${sectionIndex + 1}</label><input class="section-title-input" id="section-title-${escape(section.id)}" value="${escape(section.title)}" data-section-title="${escape(section.id)}" aria-label="Título da seção ${sectionIndex + 1}">
      <div class="icon-actions"><button class="icon-button" aria-label="Mover seção para cima" title="Mover seção para cima" data-action="section-up" data-section-id="${escape(section.id)}">↑</button><button class="icon-button" aria-label="Mover seção para baixo" title="Mover seção para baixo" data-action="section-down" data-section-id="${escape(section.id)}">↓</button><button class="icon-button" aria-label="Remover seção" title="Remover seção" data-action="remove-section" data-section-id="${escape(section.id)}">×</button></div></header>
      <div class="section-editor__body">${section.fields.map((field, index) => fieldInput(field, section, index)).join('')}<button class="button button-quiet add-field-row" data-action="add-field" data-section-id="${escape(section.id)}">＋ Adicionar informação</button></div></article>`).join('')}</div>`
      : `<div class="empty-state"><h2>Comece criando uma seção</h2><p>Por exemplo: “Quem pode participar”, “Apoio financeiro” ou “Como participar”.</p><button class="button button-primary" data-action="add-section">Adicionar primeira seção</button></div>`}
    <div class="panel-footer"><button class="button button-secondary" data-action="editor-tab" data-tab="basics">← Informações principais</button><button class="button button-primary" data-action="editor-tab" data-tab="preview">Pré-visualizar página →</button></div>
  </section>`
}
function renderPreviewTab(item) {
  return `<section class="editor-panel"><div class="preview-kicker"><div><p class="eyebrow">Como será publicado</p><h2>Prévia da página pública</h2></div><span class="status-pill ${statusClass(item)}">${item.published ? 'Publicada na demonstração' : 'Ainda não publicada'}</span></div>
    <p class="section-intro">Esta visualização usa o mesmo conteúdo e a mesma organização da página pública. A edição continua disponível antes e depois da publicação demonstrativa.</p>
    <div class="preview-frame">${renderEditorial(item, true)}</div><div class="panel-footer"><button class="button button-secondary" data-action="editor-tab" data-tab="content">← Voltar ao conteúdo</button><button class="button button-primary" data-action="toggle-publication" data-id="${escape(item.id)}">${item.published ? 'Retirar da publicação' : 'Publicar demonstração'}</button></div></section>`
}
function renderEditorial(item, preview = false) {
  const safeOfficial = safeLink(item.officialUrl)
  const fields = item.sections.map((section) => {
    const populated = section.fields.filter((field) => Array.isArray(field.value) ? field.value.some((value) => String(value).trim()) : String(field.value ?? '').trim())
    if (!section.title.trim() && !populated.length) return ''
    return `<section class="editorial-section"><h2>${escape(section.title || 'Outras informações')}</h2>${populated.map((field) => renderEditorialField(field)).join('')}</section>`
  }).join('')
  return `<div class="${preview ? 'preview-page' : ''}">${preview ? '' : `<div class="detail-topbar"><button class="back-link" data-action="go-catalog">← Voltar ao catálogo</button><span class="notice-text">Página de demonstração</span></div>`}
    <header class="editorial-header"><p class="eyebrow">${escape(item.institution || 'Instituição responsável')}</p><h1>${escape(item.title || 'Título da oportunidade')}</h1>
      <p class="editorial-summary">${escape(item.summary || 'Adicione um resumo para apresentar esta oportunidade.')}</p><div class="fact-strip">
        <div class="fact"><span>Instituição</span><strong>${escape(item.institution || 'Não informada')}</strong></div><div class="fact"><span>Prazo</span><strong>${escape(date(item.deadline))}</strong></div><div class="fact"><span>Situação</span><strong>${escape(item.status || 'Não informada')}</strong></div></div>
      ${safeOfficial ? `<a class="official-link" href="${escape(safeOfficial)}" target="_blank" rel="noopener noreferrer">Consultar página oficial ↗ <span class="notice-text">(exemplo)</span></a>` : '<p class="notice-text">URL oficial não informada</p>'}</header>
    <div class="editorial-content">${fields || '<div class="empty-state"><h2>Informações em preparação</h2><p>Esta oportunidade ainda não tem seções preenchidas.</p></div>'}</div>
    ${preview ? '<p class="public-callout">Esta é uma prévia de demonstração. Os dados não serão salvos e a publicação não é real.</p>' : ''}</div>`
}
function renderEditorialField(field) {
  const value = field.value
  if (field.type === 'link') {
    const url = safeLink(String(value ?? ''))
    return `<div class="editorial-field"><p class="editorial-field__label">${escape(field.name)}</p>${url ? `<a class="official-link" href="${escape(url)}" target="_blank" rel="noopener noreferrer">${escape(url)} ↗</a>` : '<p class="editorial-value">Link não disponível nesta demonstração.</p>'}</div>`
  }
  if (field.type === 'list') {
    const entries = Array.isArray(value) ? value.filter((entry) => String(entry).trim()) : String(value ?? '').split('\n').filter((entry) => entry.trim())
    return `<div class="editorial-field"><p class="editorial-field__label">${escape(field.name)}</p>${entries.length ? `<ul class="editorial-list">${entries.map((entry) => `<li>${escape(entry)}</li>`).join('')}</ul>` : '<p class="editorial-value">Não informado</p>'}</div>`
  }
  let display = value
  if (field.type === 'currency' && String(value ?? '').trim()) display = money(value)
  if (field.type === 'date' && value) display = date(value)
  return `<div class="editorial-field"><p class="editorial-field__label">${escape(field.name)}</p><p class="editorial-value">${escape(display || 'Não informado')}</p></div>`
}
function renderCatalog() {
  const published = state.opportunities.filter((item) => item.published)
  app.innerHTML = `<div class="container catalog-shell"><div class="catalog-header"><p class="eyebrow">Catálogo de oportunidades · demonstração</p><h1>Encontre apoio para tirar ideias do papel.</h1><p>Conheça oportunidades fictícias organizadas para facilitar sua próxima decisão.</p></div>
    ${published.length ? `<div class="catalog-cards">${published.map((item) => `<article class="catalog-item"><span class="status-pill">${escape(item.status)}</span><h2>${escape(item.title)}</h2><p>${escape(item.institution)} · Prazo: ${escape(date(item.deadline))}</p><p>${escape(item.summary)}</p><button class="button button-secondary" data-action="public-detail" data-id="${escape(item.id)}">Conhecer oportunidade →</button></article>`).join('')}</div>`
      : `<div class="empty-state"><h2>Nenhuma oportunidade publicada</h2><p>Volte mais tarde para conhecer novas oportunidades.</p></div>`}
  </div>`
}
function renderPublicDetail() {
  const item = current()
  if (!item?.published) { state.screen = 'catalog'; return renderCatalog() }
  app.innerHTML = `<div class="container detail-shell">${renderEditorial(item)}</div>`
}
function newOpportunity() {
  const item = { id: `draft-${crypto.randomUUID()}`, title: '', institution: '', summary: '', deadline: '', status: 'Inscrições abertas', officialUrl: '', published: false, sections: [] }
  state.opportunities.unshift(item); state.activeId = item.id; state.screen = 'editor'; state.editorTab = 'basics'; render()
  document.querySelector('#title')?.focus()
}
function move(list, index, direction) {
  const other = index + direction
  if (other < 0 || other >= list.length) return
  ;[list[index], list[other]] = [list[other], list[index]]
}
function setScreen(name) { state.screen = name; state.activeId = name === 'editor' ? state.activeId : null; render(); window.scrollTo(0, 0) }

app.addEventListener('input', (event) => {
  const target = event.target
  if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement)) return
  if (target.matches('[data-search]')) { state.search = target.value; renderAdminList(); const input = app.querySelector('[data-search]'); input?.focus(); input?.setSelectionRange(state.search.length, state.search.length); return }
  const item = current()
  if (!item) return
  const main = target.dataset.main
  if (main) { item[main] = target.value; if (target.id === 'title') document.querySelector('.editor-heading h1').textContent = target.value || 'Nova oportunidade'; updatePreviewInPlace(); return }
  const sectionTitleId = target.dataset.sectionTitle
  if (sectionTitleId) { const section = item.sections.find((entry) => entry.id === sectionTitleId); if (section) section.title = target.value; return }
  const fieldId = target.dataset.fieldId
  if (fieldId && target.dataset.key) {
    const field = item.sections.find((section) => section.id === target.dataset.sectionId)?.fields.find((entry) => entry.id === fieldId)
    if (field) field[target.dataset.key] = target.dataset.key === 'value' && field.type === 'list' ? target.value.split('\n') : target.value
    updatePreviewInPlace()
  }
})
function updatePreviewInPlace() {
  const preview = app.querySelector('.preview-frame')
  if (preview) preview.innerHTML = renderEditorial(current(), true)
}
app.addEventListener('change', (event) => {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) return
  const fieldId = target.dataset.fieldId
  if (!fieldId || target.dataset.key !== 'type') return
  const field = current()?.sections.find((section) => section.id === target.dataset.sectionId)?.fields.find((entry) => entry.id === fieldId)
  if (!field) return
  const prior = field.value
  field.type = target.value
  field.value = target.value === 'list' ? Array.isArray(prior) ? prior : String(prior ?? '').split('\n').filter(Boolean)
    : Array.isArray(prior) ? prior.join('\n') : prior
  render()
})
app.addEventListener('click', (event) => {
  const button = event.target.closest('[data-action]')
  if (!button) return
  const action = button.dataset.action
  if (action === 'go-admin') return setScreen('admin-list')
  if (action === 'go-catalog') return setScreen('catalog')
  if (action === 'new-opportunity') return newOpportunity()
  if (action === 'edit') { state.activeId = button.dataset.id; state.screen = 'editor'; state.editorTab = 'basics'; return render() }
  if (action === 'editor-tab') { state.editorTab = button.dataset.tab; return render() }
  if (action === 'public-detail') { state.activeId = button.dataset.id; state.screen = 'public-detail'; return render() }
  const item = current()
  if (action === 'toggle-publication' && item) {
    if (!item.published && (!item.title.trim() || !item.institution.trim())) { alert('Informe o título e a instituição responsável antes de publicar a demonstração.'); return }
    item.published = !item.published; return render()
  }
  if (!item) return
  if (action === 'add-section') { item.sections.push({ id: crypto.randomUUID(), title: 'Nova seção', fields: [] }); render(); app.querySelector(`[data-section-title="${CSS.escape(item.sections.at(-1).id)}"]`)?.focus() }
  if (action === 'remove-section') { const index = item.sections.findIndex((section) => section.id === button.dataset.sectionId); if (index >= 0 && confirm('Remover esta seção e suas informações da demonstração?')) { item.sections.splice(index, 1); render() } }
  if (action === 'section-up' || action === 'section-down') { const index = item.sections.findIndex((section) => section.id === button.dataset.sectionId); move(item.sections, index, action === 'section-up' ? -1 : 1); render() }
  if (action === 'add-field') { const section = item.sections.find((entry) => entry.id === button.dataset.sectionId); if (section) { const field = makeField('Nova informação', 'short-text', ''); section.fields.push(field); render(); document.querySelector(`[data-field-id="${CSS.escape(field.id)}"][data-key="name"]`)?.focus() } }
  if (action === 'remove-field') { const section = item.sections.find((entry) => entry.id === button.dataset.sectionId); const index = section?.fields.findIndex((field) => field.id === button.dataset.fieldId) ?? -1; if (section && index >= 0 && confirm('Remover esta informação da demonstração?')) { section.fields.splice(index, 1); render() } }
  if (action === 'field-up' || action === 'field-down') { const section = item.sections.find((entry) => entry.id === button.dataset.sectionId); const index = section?.fields.findIndex((field) => field.id === button.dataset.fieldId) ?? -1; if (section) { move(section.fields, index, action === 'field-up' ? -1 : 1); render() } }
})

document.querySelector('.site-header nav').addEventListener('click', (event) => {
  const button = event.target.closest('[data-action]')
  if (!button) return
  if (button.dataset.action === 'go-admin') setScreen('admin-list')
  if (button.dataset.action === 'go-catalog') setScreen('catalog')
})

render()
