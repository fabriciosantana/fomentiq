(() => {
  const opportunities = [
    {
      id: 'aurora',
      title: 'Apoio à infraestrutura de pesquisa',
      institution: 'Instituto Aurora · organização fictícia',
      source: 'Agência Aurora · demonstração',
      status: 'Inscrições abertas',
      deadline: '31 ago 2026',
      budget: 'Até R$ 8 milhões',
      pending: 3,
      documents: [{ title: 'Edital de apoio a pesquisa e infraestrutura', detail: 'Versão fictícia · publicado em 20 de janeiro de 2026' }],
      sections: [
        { id: 'general', title: 'Informações gerais', values: [
          { key: 'prazo', label: 'Prazo de inscrição', value: '31 de agosto de 2026, às 18h', status: 'attention', source: 'Edital de demonstração · seção 3.2', sourceTitle: 'Edital de apoio a pesquisa e infraestrutura', sourceLocation: 'Seção 3.2', quote: 'As propostas poderão ser enviadas até 31 de agosto de 2026, às 18 horas, horário de Brasília.' },
          { key: 'instrument', label: 'Tipo de apoio', value: 'Financiamento não reembolsável', status: 'done' },
          { key: 'program', label: 'Programa', value: 'Pesquisa e infraestrutura', status: 'done' },
        ]},
        { id: 'participation', title: 'Quem pode participar', values: [
          { key: 'eligibility', label: 'Instituições elegíveis', value: 'Instituições de pesquisa sem fins lucrativos', status: 'attention', source: 'Edital de demonstração · seção 2.1', sourceTitle: 'Edital de apoio a pesquisa e infraestrutura', sourceLocation: 'Seção 2.1', quote: 'Podem participar instituições de pesquisa, públicas ou privadas sem fins lucrativos, com sede no país.' },
          { key: 'regions', label: 'Abrangência', value: 'Nacional', status: 'done' },
        ]},
        { id: 'finance', title: 'Condições financeiras', values: [
          { key: 'budget', label: 'Orçamento por proposta', value: 'Até R$ 8 milhões', status: 'done' },
          { key: 'counterpart', label: 'Contrapartida', value: 'Condição varia conforme o porte da instituição', status: 'attention', source: 'Edital de demonstração · seção 5.4', sourceTitle: 'Edital de apoio a pesquisa e infraestrutura', sourceLocation: 'Seção 5.4', quote: 'A participação financeira será definida conforme a categoria e o porte da instituição proponente.' },
        ]},
        { id: 'documents', title: 'Documentos e requisitos', values: [
          { key: 'documents', label: 'Documentos para inscrição', value: 'Plano de trabalho e orçamento detalhado', status: 'done' },
          { key: 'trl', label: 'Nível de maturidade tecnológica', value: 'Não identificado na extração', status: 'attention', source: 'Edital de demonstração · documento revisado', sourceTitle: 'Edital de apoio a pesquisa e infraestrutura', sourceLocation: 'Documento revisado', quote: null },
        ]},
      ],
    },
    {
      id: 'campo',
      title: 'Pesquisa aplicada em tecnologias sustentáveis',
      institution: 'Centro Campo Claro · organização fictícia',
      source: 'Conselho Horizonte · demonstração',
      status: 'Inscrições em breve',
      deadline: 'Prazo em revisão',
      budget: 'Valor em revisão',
      pending: 1,
      documents: [{ title: 'Comunicado complementar de demonstração', detail: 'Versão fictícia · publicado em 3 de março de 2026' }],
      sections: [
        { id: 'general', title: 'Informações gerais', values: [
          { key: 'prazo-campo', label: 'Prazo de inscrição', value: '15 de setembro de 2026', status: 'attention', source: 'Comunicado de demonstração · seção 1.3', sourceTitle: 'Comunicado complementar de demonstração', sourceLocation: 'Seção 1.3', quote: 'O prazo de envio será encerrado em 15 de setembro de 2026.' },
          { key: 'program-campo', label: 'Programa', value: 'Tecnologias sustentáveis', status: 'done' },
        ]},
        { id: 'participation', title: 'Quem pode participar', values: [
          { key: 'eligible-campo', label: 'Instituições elegíveis', value: 'Instituições de ensino e pesquisa', status: 'done' },
        ]},
        { id: 'finance', title: 'Condições financeiras', values: [
          { key: 'budget-campo', label: 'Orçamento', value: 'Em análise', status: 'done' },
        ]},
        { id: 'documents', title: 'Documentos e requisitos', values: [
          { key: 'docs-campo', label: 'Documentos para inscrição', value: 'Plano de trabalho', status: 'done' },
        ]},
      ],
    },
  ]

  const app = document.querySelector('#app')
  const state = { opportunityId: 'aurora', query: '', selectedKey: 'prazo', activeReviewKey: '', decision: 'confirm', correctedValue: '', justification: '', summary: false, notice: '', reviewed: new Set() }
  const currentOpportunity = () => opportunities.find((item) => item.id === state.opportunityId) || opportunities[0]
  const allFields = (opportunity) => opportunity.sections.flatMap((section) => section.values.map((field) => ({ ...field, section: section.title })))
  const reviewedCount = (opportunity) => [...state.reviewed].filter((key) => key.startsWith(`${opportunity.id}:`)).length
  const findField = () => allFields(currentOpportunity()).find((field) => field.key === state.selectedKey) || allFields(currentOpportunity())[0]
  const statusOf = (field) => state.reviewed.has(`${currentOpportunity().id}:${field.key}`) ? 'done' : field.status
  const screenFromHash = () => {
    const route = location.hash.replace(/^#\/?/, '')
    if (route.startsWith('revisar/')) {
      const [, key] = route.split('/')
      const selectedKey = decodeURIComponent(key || 'prazo')
      const reviewKey = `${state.opportunityId}:${selectedKey}`
      if (state.activeReviewKey !== reviewKey) {
        state.activeReviewKey = reviewKey
        state.decision = 'confirm'
        state.correctedValue = ''
        state.justification = ''
        state.summary = false
      }
      state.selectedKey = selectedKey
      return 'review'
    }
    if (route.startsWith('oportunidade/')) {
      state.opportunityId = route.split('/')[1] || 'aurora'
      return 'overview'
    }
    return 'queue'
  }
  const go = (route) => {
    state.summary = false
    state.notice = ''
    if (location.hash === `#${route}`) render()
    else location.hash = route
  }
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char])
  const tag = (field) => statusOf(field) === 'done'
    ? '<span class="done-chip">✓ Revisado</span>' : '<span class="attention-chip">Precisa conferir</span>'

  function renderQueue() {
    const filtered = opportunities.filter((item) => `${item.title} ${item.institution} ${item.source}`.toLocaleLowerCase('pt-BR').includes(state.query.toLocaleLowerCase('pt-BR')))
    app.innerHTML = `<section class="page" aria-labelledby="page-title">
      <div class="page-heading"><div><p class="eyebrow">Sua central de oportunidades</p><h1 id="page-title">O que precisa da sua atenção?</h1><p class="lede">Confira as informações importantes e ajude a deixar cada oportunidade pronta para consulta.</p></div><div class="queue-count"><strong>${opportunities.reduce((sum, item) => sum + Math.max(0, item.pending - reviewedCount(item)), 0)}</strong> informações para conferir</div></div>
      <label class="search-wrap"><span aria-hidden="true">⌕</span><span class="visually-hidden">Buscar oportunidade</span><input id="queue-search" type="search" placeholder="Buscar por oportunidade ou instituição" value="${escapeHtml(state.query)}"></label>
      <div class="queue-list" aria-label="Oportunidades">
        ${filtered.map((item) => { const pending = Math.max(0, item.pending - reviewedCount(item)); return `<article class="opportunity-card"><div class="card-main"><p class="card-source">${escapeHtml(item.source)} <span aria-hidden="true">·</span> ${escapeHtml(item.institution)}</p><h2>${escapeHtml(item.title)}</h2><div class="card-meta"><span>${escapeHtml(item.status)}</span><span>Prazo: ${escapeHtml(item.deadline)}</span></div></div><div>${pending > 0 ? `<span class="pending-chip">● ${pending} para conferir</span>` : '<span class="done-chip">✓ Revisada</span>'}<div style="height:.65rem"></div><a class="button button--primary" href="#oportunidade/${encodeURIComponent(item.id)}">${pending > 0 ? 'Continuar revisão' : 'Ver oportunidade'} <span aria-hidden="true">→</span></a></div></article>` }).join('') || '<p class="empty">Nenhuma oportunidade corresponde à busca.</p>'}
      </div>
    </section>`
    app.querySelector('#queue-search').addEventListener('input', (event) => { state.query = event.target.value; renderQueue(); const input = app.querySelector('#queue-search'); input.focus(); input.setSelectionRange(input.value.length, input.value.length) })
  }

  function renderOverview() {
    const opportunity = currentOpportunity()
    const fields = allFields(opportunity)
    const remaining = fields.filter((field) => statusOf(field) !== 'done').length
    app.innerHTML = `<section class="page" aria-labelledby="page-title">
      <nav class="breadcrumb" aria-label="Navegação"><a href="#fila">Fila de revisão</a><span aria-hidden="true">/</span><span>Visão geral</span></nav>
      <div class="overview-head"><div><p class="eyebrow">${escapeHtml(opportunity.source)}</p><h1 id="page-title">${escapeHtml(opportunity.title)}</h1><p class="lede">${escapeHtml(opportunity.institution)}</p></div><span class="${remaining ? 'pending-chip' : 'done-chip'}">${remaining ? `${remaining} informações para conferir` : 'Tudo revisado'}</span></div>
      <dl class="overview-facts"><div><dt>Instituição responsável</dt><dd>${escapeHtml(opportunity.institution.split(' · ')[0])}</dd></div><div><dt>Situação</dt><dd>${escapeHtml(opportunity.status)}</dd></div><div><dt>Prazo de inscrição</dt><dd>${escapeHtml(opportunity.deadline)}</dd></div><div><dt>Orçamento</dt><dd>${escapeHtml(opportunity.budget)}</dd></div></dl>
      ${opportunity.sections.map((section) => {
        const needsReview = section.values.some((field) => statusOf(field) !== 'done')
        return `<section aria-labelledby="section-${section.id}"><div class="section-heading"><h2 id="section-${section.id}">${section.title}</h2><span class="section-state">${needsReview ? 'Há informação para conferir' : 'Informações revisadas'}</span></div><div class="info-list">${section.values.map((field) => `<div class="info-row"><span class="info-name">${field.label}</span><span class="info-value">${escapeHtml(field.value)}${field.source ? `<small>Fonte: ${escapeHtml(field.source)}</small>` : ''}</span><span class="review-indicator ${statusOf(field) === 'done' ? 'review-indicator--done' : ''}">${statusOf(field) === 'done' ? 'Revisado' : 'Conferir'}</span></div>`).join('')}</div>${needsReview ? `<div class="section-action"><button class="button button--secondary" data-review-section="${section.id}">Revisar informações desta seção <span aria-hidden="true">→</span></button></div>` : ''}</section>`
      }).join('')}
      <section aria-labelledby="documents-title"><div class="section-heading"><h2 id="documents-title">Documentos da oportunidade</h2><span class="section-state">${opportunity.documents.length} ${opportunity.documents.length === 1 ? 'documento' : 'documentos'} de demonstração</span></div>${opportunity.documents.map((document) => `<div class="document-summary"><strong>${escapeHtml(document.title)} <span class="pending-chip">Documento fictício</span></strong><p>${escapeHtml(document.detail)}</p><button class="button button--quiet" data-open-source>Ver documentos e requisitos <span aria-hidden="true">→</span></button></div>`).join('')}</section>
      <div class="action-row"><a href="#fila" class="button button--quiet">← Voltar para a fila</a>${remaining ? `<button class="button button--primary" data-next-review>Revisar próxima informação <span aria-hidden="true">→</span></button>` : ''}</div>
    </section>`
    app.querySelectorAll('[data-review-section]').forEach((button) => button.addEventListener('click', () => {
      const section = opportunity.sections.find((item) => item.id === button.dataset.reviewSection)
      const field = section?.values.find((item) => statusOf(item) !== 'done') || section?.values[0]
      if (field) go(`#revisar/${encodeURIComponent(field.key)}`)
    }))
    app.querySelector('[data-next-review]')?.addEventListener('click', () => {
      const next = fields.find((field) => statusOf(field) !== 'done')
      if (next) go(`#revisar/${encodeURIComponent(next.key)}`)
    })
    app.querySelector('[data-open-source]')?.addEventListener('click', () => { state.notice = 'Os documentos e trechos desta tela são fictícios e servem apenas para avaliar o fluxo.'; renderOverview() })
    if (state.notice) app.insertAdjacentHTML('afterbegin', `<p class="notice" role="status">${escapeHtml(state.notice)}</p>`)
  }

  function renderReview() {
    const opportunity = currentOpportunity()
    const field = findField()
    if (!field) { app.innerHTML = '<section class="page"><p class="empty">Não há informações para revisar nesta demonstração.</p><a href="#fila" class="button button--primary">Voltar à fila</a></section>'; return }
    const fields = allFields(opportunity)
    const pendingFields = fields.filter((item) => statusOf(item) !== 'done')
    const currentIndex = Math.max(0, pendingFields.findIndex((item) => item.key === field.key))
    const doneCount = fields.length - pendingFields.length
    const nextValue = state.decision === 'correct' ? state.correctedValue : field.value
    const actionText = state.decision === 'correct' ? 'corrigir a informação' : state.decision === 'absent' ? 'registrar que a informação não foi localizada' : 'confirmar a informação atual'
    app.innerHTML = `<section class="page page--narrow" aria-labelledby="page-title">
      <nav class="breadcrumb" aria-label="Navegação"><a href="#fila">Fila de revisão</a><span aria-hidden="true">/</span><a href="#oportunidade/${encodeURIComponent(opportunity.id)}">${escapeHtml(opportunity.title)}</a><span aria-hidden="true">/</span><span>Revisar informação</span></nav>
      <div class="review-progress"><span>${doneCount} de ${fields.length} informações já conferidas <span aria-hidden="true">·</span> ${pendingFields.length ? `esta é a ${currentIndex + 1}ª de ${pendingFields.length} pendentes` : 'todas concluídas'}</span><span class="progress-track" role="progressbar" aria-label="Progresso da revisão" aria-valuemin="0" aria-valuemax="${fields.length}" aria-valuenow="${doneCount}"><span style="width:${Math.round(doneCount / fields.length * 100)}%"></span></span></div>
      <div class="review-title"><p class="eyebrow">${escapeHtml(field.section)}</p><h1 id="page-title">${field.key === 'prazo' ? 'O prazo de inscrição está correto?' : field.key === 'eligibility' ? 'Quem pode participar?' : field.key === 'counterpart' ? 'A condição financeira está correta?' : `A informação “${field.label.toLocaleLowerCase('pt-BR')}” está correta?`}</h1><p>Confira o valor e a fonte. Você pode confirmar, corrigir ou registrar que não encontrou essa informação.</p></div>
      <div class="decision-layout">
        <section class="panel"><div class="panel__body"><h2>Informação atual</h2><p class="current-value"><span>Identificada na oportunidade</span><strong>${escapeHtml(field.value)}</strong></p><p style="margin:.8rem 0 0">${tag(field)} <span class="source-note">Esta é uma informação em revisão, ainda não uma confirmação sua.</span></p>
          <form id="review-form"><fieldset style="border:0;padding:0;margin:1.2rem 0 0"><legend class="visually-hidden">O que você quer fazer?</legend><div class="choice-list">
            <label class="choice"><input type="radio" name="decision" value="confirm" ${state.decision === 'confirm' ? 'checked' : ''}><span><strong>Confirmar este valor</strong><small>A fonte mostra a mesma informação.</small></span></label>
            <label class="choice"><input type="radio" name="decision" value="correct" ${state.decision === 'correct' ? 'checked' : ''}><span><strong>Corrigir o valor</strong><small>Você encontrou uma informação diferente na fonte.</small></span></label>
            <label class="choice"><input type="radio" name="decision" value="absent" ${state.decision === 'absent' ? 'checked' : ''}><span><strong>Registrar que não foi localizado</strong><small>Use quando revisou a fonte e não encontrou essa informação.</small></span></label>
          </div></fieldset>
          ${state.decision === 'correct' ? `<label class="form-field"><span>${field.label}</span><input id="corrected-value" type="text" ${field.key.startsWith('prazo') ? 'inputmode="numeric" placeholder="DD/MM/AAAA às HH:MM"' : ''} value="${escapeHtml(state.correctedValue)}" required><small class="form-hint">${field.key.startsWith('prazo') ? 'Formato: dia/mês/ano e horário de Brasília.' : 'Informe somente o que está amparado pelo documento.'}</small></label>` : ''}
          ${state.decision === 'absent' ? '<p class="notice">A ausência só será registrada após revisar as versões documentais disponíveis.</p>' : ''}
          <label class="form-field"><span>Justificativa <small>${state.decision === 'absent' ? '(necessária para registrar a busca)' : '(opcional ao confirmar)'}</small></span><textarea id="justification" ${state.decision === 'absent' ? 'required' : ''} placeholder="O que você revisou e onde procurou?">${escapeHtml(state.justification)}</textarea></label>
          <div class="current-value" style="margin-top:.85rem"><span>Referência documental</span><strong>${escapeHtml(field.sourceTitle || 'Documento de demonstração')} · ${escapeHtml(field.sourceLocation || 'localização fictícia')}</strong></div>
          <p class="form-hint">A citação ao lado é apenas demonstrativa. No produto, a referência virá do documento preservado.</p>
          <div class="action-row"><a href="#oportunidade/${encodeURIComponent(opportunity.id)}" class="button button--quiet">Voltar à visão geral</a><button class="button button--primary" type="submit">Ver resumo da alteração <span aria-hidden="true">→</span></button></div>
        </form>
        ${state.summary ? `<section class="summary-card" aria-live="polite"><h2>Confira antes de publicar</h2><p><strong>Ação:</strong> ${actionText}.</p><p><strong>${state.decision === 'absent' ? 'Resultado da revisão' : 'Valor após a revisão'}:</strong> ${escapeHtml(state.decision === 'absent' ? 'Informação não localizada após revisão documental' : nextValue || 'Não informado')}.</p><p><strong>Fonte:</strong> ${escapeHtml(field.source || 'Edital de demonstração · versão fictícia')}.</p>${state.justification.trim() ? `<p><strong>Justificativa:</strong> ${escapeHtml(state.justification.trim())}</p>` : ''}<div class="summary-actions"><button class="button button--secondary" data-back-edit>Voltar e editar</button><button class="button button--primary" data-simulate>Simular registro</button></div></section>` : ''}
        ${state.notice ? `<p class="notice" role="status">${escapeHtml(state.notice)}</p>` : ''}
        <details class="history"><summary>Ver histórico desta informação</summary><div class="history-list"><div class="history-item"><p><strong>Extração inicial · demonstração</strong></p><p>Valor registrado: ${escapeHtml(field.value)}</p><p>Fonte e versão preservadas (dados fictícios).</p></div><div class="history-item"><p><strong>Revisão anterior</strong></p><p>Nenhuma revisão publicada neste protótipo.</p></div></div></details>
        </div></section>
        <aside class="source-context"><div class="source-card"><div class="source-card__top"><span>Fonte da informação</span><span>Documento fictício</span></div><h2>${escapeHtml(field.sourceTitle || 'Documento de demonstração')}</h2><p class="source-meta">Versão de demonstração · ${escapeHtml(field.sourceLocation || 'Localização fictícia')}</p>${field.quote ? `<blockquote class="source-quote">“${escapeHtml(field.quote)}”</blockquote>` : '<p class="source-note">Nenhum trecho foi selecionado para este exemplo. A ausência documental não pode ser confirmada sem revisar as versões disponíveis.</p>'}<p class="source-note">Trechos e localizações são fictícios. Nenhum documento é aberto ou baixado.</p><button class="button button--quiet" type="button" data-expand-source>Ver referência do documento</button><p class="source-note" data-source-extra hidden>Documento de demonstração · nenhuma URL externa · nenhuma chave de armazenamento.</p></div></aside>
      </div>
    </section>`
    app.querySelectorAll('input[name="decision"]').forEach((input) => input.addEventListener('change', (event) => {
      state.justification = app.querySelector('#justification')?.value ?? state.justification
      state.correctedValue = app.querySelector('#corrected-value')?.value ?? state.correctedValue
      state.decision = event.target.value
      state.summary = false
      renderReview()
    }))
    app.querySelector('#review-form').addEventListener('submit', (event) => {
      event.preventDefault()
      state.justification = app.querySelector('#justification').value
      const correction = app.querySelector('#corrected-value')
      state.correctedValue = correction ? correction.value : state.correctedValue
      if (state.decision === 'correct' && !state.correctedValue.trim()) return
      state.summary = true
      renderReview()
    })
    app.querySelector('[data-back-edit]')?.addEventListener('click', () => { state.summary = false; renderReview() })
    app.querySelector('[data-simulate]')?.addEventListener('click', () => {
      state.reviewed.add(`${opportunity.id}:${field.key}`)
      const hasMore = fields.some((item) => item.key !== field.key && statusOf(item) !== 'done')
      state.notice = 'Simulação concluída somente nesta aba. Nenhum dado foi enviado ou salvo.'
      state.summary = false
      if (hasMore) {
        const next = fields.find((item) => statusOf(item) !== 'done')
        state.selectedKey = next.key
        state.activeReviewKey = `${opportunity.id}:${next.key}`
        state.decision = 'confirm'
        state.correctedValue = ''
        state.justification = ''
        renderReview()
      } else {
        go(`#oportunidade/${encodeURIComponent(opportunity.id)}`)
      }
    })
    app.querySelector('[data-expand-source]').addEventListener('click', () => {
      const extra = app.querySelector('[data-source-extra]')
      extra.hidden = !extra.hidden
      app.querySelector('[data-expand-source]').textContent = extra.hidden ? 'Ver referência do documento' : 'Ocultar detalhes da referência'
    })
  }

  function render() {
    const screen = screenFromHash()
    if (screen === 'overview') renderOverview()
    else if (screen === 'review') renderReview()
    else renderQueue()
    app.focus({ preventScroll: true })
    window.scrollTo(0, 0)
  }
  window.addEventListener('hashchange', () => {
    state.summary = false
    state.notice = ''
    render()
  })
  if (!location.hash) location.hash = '#fila'
  else render()
})()
