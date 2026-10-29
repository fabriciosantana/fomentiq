import { useEffect, useState, type FormEvent } from 'react'
import { Link, useBlocker, useNavigate, useParams } from 'react-router-dom'
import { adminLogin, fetchAdminCsrf, fetchAdminSession, type CsrfBootstrap } from './api/opportunities'
import {
  createManualOpportunity, fetchEditorialCatalog, fetchEditorialOpportunity, fetchManualAdminList,
  fetchManualAdminOpportunity, saveManualOpportunity, transitionManualOpportunity,
  type EditorialField, type EditorialFieldType, type EditorialOpportunity, type EditorialSection,
} from './api/opportunities'
import './ManualEditorialPage.css'

const TYPES: { value: EditorialFieldType; label: string }[] = [
  { value: 'SHORT_TEXT', label: 'Texto curto' }, { value: 'LONG_TEXT', label: 'Texto longo' },
  { value: 'DATE', label: 'Data' }, { value: 'CURRENCY', label: 'Moeda' },
  { value: 'LINK', label: 'Link' }, { value: 'LIST', label: 'Lista' },
]
const blank = (): Omit<EditorialOpportunity, 'id' | 'rowVersion'> => ({ title: '', institution: '', summary: '', deadline: null,
  status: 'Inscrições abertas', officialUrl: '', editorialState: 'DRAFT', sections: [] })
const safeUrl = (value: string | null) => {
  if (!value) return null
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : null }
  catch { return null }
}
const formatDate = (value: string | null) => {
  if (!value) return 'A definir'
  const [year, month, day] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(Date.UTC(year, month - 1, day, 12)))
}
const formatValue = (field: EditorialField) => {
  if (field.value == null || field.value === '') return 'Não informado'
  if (field.type === 'DATE') return formatDate(String(field.value))
  if (field.type === 'CURRENCY') return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(field.value))
  return String(field.value)
}
function Shell({ children, admin = false }: { children: React.ReactNode; admin?: boolean }) {
  return <><div className="editorial-banner">Fomentiq · oportunidades para transformar ideias em inovação</div>
    <header className="editorial-nav"><Link to="/" className="editorial-logo">fomentiq<span>·</span></Link>
      <nav aria-label="Navegação principal"><Link to="/">Catálogo</Link>{admin && <Link to="/admin">Administração</Link>}</nav>
      <span className="editorial-nav__caption">Inteligência para inovar</span></header>{children}
    <footer className="editorial-footer"><span>fomentiq</span><span>Oportunidades para transformar ideias em inovação.</span></footer></>
}

export function EditorialCatalogPage() {
  const [q, setQ] = useState(''); const [search, setSearch] = useState('')
  const [items, setItems] = useState<EditorialOpportunity[] | null>(null); const [error, setError] = useState('')
  useEffect(() => { let active = true; fetchEditorialCatalog(search).then(r => { if (active) setItems(r.content) }).catch(() => { if (active) setError('Não foi possível carregar as oportunidades.') }); return () => { active = false } }, [search])
  function submit(event: FormEvent) { event.preventDefault(); setItems(null); setError(''); setSearch(q.trim()) }
  return <Shell><main className="editorial-container editorial-catalog"><p className="editorial-eyebrow">CATÁLOGO DE OPORTUNIDADES</p>
    <h1>Encontre apoio para tirar ideias do papel.</h1><p className="editorial-lead">Conheça oportunidades de fomento organizadas para facilitar sua próxima decisão.</p>
    <form className="editorial-search" onSubmit={submit}><label htmlFor="catalog-search">Buscar oportunidade ou instituição</label>
      <div><input id="catalog-search" value={q} onChange={e => setQ(e.target.value)} placeholder="Digite um nome ou tema"/><button className="editorial-button editorial-button--primary">Buscar</button></div></form>
    {items === null && !error && <p role="status">Carregando oportunidades…</p>}{error && <p role="alert">{error}</p>}
    {items?.length === 0 && <p className="editorial-empty">Nenhuma oportunidade publicada encontrada.</p>}
    {!!items?.length && <div className="editorial-list">{items.map(item => <article className="editorial-list-item" key={item.id}>
      <p className="editorial-eyebrow">{item.institution} · {item.status}</p><h2>{item.title}</h2><p>{item.summary}</p>
      <p className="editorial-list-item__meta">Prazo: {formatDate(item.deadline)}</p><Link className="editorial-text-link" to={`/catalog/${item.id}`}>Conhecer oportunidade →</Link>
    </article>)}</div>}
    <p className="editorial-legacy"><Link to="/radar">Consultar catálogo histórico de chamadas importadas</Link></p>
  </main></Shell>
}

export function EditorialOpportunityPage() {
  const { id: itemId = '' } = useParams(); const [item, setItem] = useState<EditorialOpportunity | null>(null); const [error, setError] = useState('')
  useEffect(() => { let active = true; fetchEditorialOpportunity(itemId).then(value => { if (active) setItem(value) }).catch(() => { if (active) setError('Esta oportunidade não está disponível.') }); return () => { active = false } }, [itemId])
  return <Shell><main className="editorial-container editorial-detail"><Link to="/" className="editorial-back">← Voltar ao catálogo</Link>
    {!item && !error && <p role="status">Carregando oportunidade…</p>}{error && <section className="editorial-empty"><h1>Oportunidade não encontrada</h1><p>{error}</p></section>}
    {item && <><header className="editorial-detail-header"><p className="editorial-eyebrow">{item.institution}</p><h1>{item.title}</h1><p className="editorial-lead">{item.summary}</p>
      <dl className="editorial-facts"><div><dt>Instituição responsável</dt><dd>{item.institution}</dd></div><div><dt>Prazo</dt><dd>{formatDate(item.deadline)}</dd></div><div><dt>Situação</dt><dd>{item.status}</dd></div></dl>
      {safeUrl(item.officialUrl) && <a className="editorial-button editorial-button--secondary" href={safeUrl(item.officialUrl)!} target="_blank" rel="noopener noreferrer">Consultar página oficial ↗</a>}</header>
      <div className="editorial-sections">{item.sections.map(section => <section key={`${section.position}-${section.title}`}><h2>{section.title}</h2>
        {section.fields.map(field => <div className="editorial-field" key={`${field.position}-${field.name}`}><h3>{field.name}</h3>
          {field.type === 'LIST' && Array.isArray(field.value) ? <ul>{field.value.map((entry,index)=><li key={`${index}-${entry}`}>{entry}</li>)}</ul>
            : field.type === 'LINK' && safeUrl(String(field.value ?? '')) ? <a href={safeUrl(String(field.value))!} target="_blank" rel="noopener noreferrer">{String(field.value)} ↗</a>
            : <p>{formatValue(field)}</p>}</div>)}</section>)}</div></>}
  </main></Shell>
}

function AdminLogin({ onLogin }: { onLogin: (username: string,password: string) => Promise<void> }) {
  const [error,setError]=useState(''); const [busy,setBusy]=useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setBusy(true); setError(''); const data=new FormData(event.currentTarget)
    try { await onLogin(String(data.get('username')),String(data.get('password'))) } catch { setError('Não foi possível entrar. Confira as credenciais do ambiente de demonstração.') } finally { setBusy(false) } }
  return <main className="editorial-container editorial-admin"><section className="editorial-panel editorial-login"><p className="editorial-eyebrow">ADMINISTRAÇÃO · DEMONSTRAÇÃO ISOLADA</p><h1>Acesso administrativo</h1>
    <form onSubmit={e=>void submit(e)}><label>Usuário<input name="username" autoComplete="username" required/></label><label>Senha<input name="password" type="password" autoComplete="current-password" required/></label>
      {error&&<p role="alert">{error}</p>}<button disabled={busy} className="editorial-button editorial-button--primary">{busy?'Entrando…':'Entrar'}</button></form></section></main>
}

export function ManualOpportunityAdminPage() {
  const { id: routeId }=useParams(); const navigate=useNavigate(); const [session,setSession]=useState<boolean|null>(null); const [csrf,setCsrf]=useState<CsrfBootstrap|null>(null)
  const [items,setItems]=useState<EditorialOpportunity[]>([]); const [search,setSearch]=useState(''); const [item,setItem]=useState<EditorialOpportunity|null>(null)
  const [error,setError]=useState(''); const [message,setMessage]=useState(''); const [busy,setBusy]=useState(false); const [preview,setPreview]=useState(false)
  const [savedJSON,setSavedJSON]=useState('')
  const dirty=!!item&&JSON.stringify(item)!==savedJSON
  const blocker=useBlocker(({currentLocation,nextLocation})=>dirty&&currentLocation.pathname!==nextLocation.pathname)
  useEffect(()=>{if(blocker.state!=='blocked')return;if(window.confirm('Há alterações não salvas. Sair mesmo assim?'))blocker.proceed();else blocker.reset()},[blocker])
  useEffect(()=>{let active=true; Promise.all([fetchAdminCsrf(),fetchAdminSession()]).then(([token])=>{if(active){setCsrf(token);setSession(true)}}).catch(()=>{if(active)setSession(false)});return()=>{active=false}},[])
  useEffect(()=>{if(!session||routeId||!csrf)return;fetchManualAdminList(search).then(r=>setItems(r.content)).catch(()=>setError('Não foi possível carregar as oportunidades.'));},[session,routeId,search,csrf])
  useEffect(()=>{if(!session||!routeId||routeId==='new')return;fetchManualAdminOpportunity(routeId).then(value=>{setItem(value);setSavedJSON(JSON.stringify(value))}).catch(()=>setError('Não foi possível abrir esta oportunidade.'));},[session,routeId])
  async function login(username:string,password:string){const token=await fetchAdminCsrf();await adminLogin(username,password,token);const rotatedToken=await fetchAdminCsrf();setCsrf(rotatedToken);await fetchAdminSession();setSession(true)}
  useEffect(()=>{const handler=(event:BeforeUnloadEvent)=>{if(dirty){event.preventDefault();event.returnValue=''}};window.addEventListener('beforeunload',handler);return()=>window.removeEventListener('beforeunload',handler)},[dirty])
  async function create(){if(!csrf)return;setBusy(true);setError('');try{const created=await createManualOpportunity({...blank(),sections:[]},csrf);setItem(created);setSavedJSON(JSON.stringify(created));navigate(`/admin/manual/${created.id}`,{replace:true})}catch(e){setError(e instanceof Error?e.message:'Não foi possível criar o rascunho.')}finally{setBusy(false)}}
  async function save(){if(!item||!csrf)return item;setBusy(true);setError('');try{const result=await saveManualOpportunity(item,csrf);setItem(result);setSavedJSON(JSON.stringify(result));setMessage('Rascunho salvo. A publicação atual não foi alterada.');return result}catch(e){setError(e instanceof Error?e.message:'Não foi possível salvar.');return null}finally{setBusy(false)}}
  async function publish(){if(!item||!csrf)return;const current=dirty?await save():item;if(!current)return;setBusy(true);setError('');try{const result=await transitionManualOpportunity(current.id,'publish',current.rowVersion??0,csrf);setItem(result);setSavedJSON(JSON.stringify(result));setMessage('Oportunidade publicada.');}catch(e){setError(e instanceof Error?e.message:'Não foi possível publicar.')}finally{setBusy(false)}}
  async function unpublish(){if(!item||!csrf)return;if(dirty&&!window.confirm('Há alterações não salvas. Retirar a publicação descartará essas alterações. Continuar?'))return;setBusy(true);try{const result=await transitionManualOpportunity(item.id,'unpublish',item.rowVersion??0,csrf);setItem(result);setSavedJSON(JSON.stringify(result));setMessage('Publicação retirada. O conteúdo continua salvo como rascunho.')}catch(e){setError(e instanceof Error?e.message:'Não foi possível retirar a publicação.')}finally{setBusy(false)}}
  function update(patch:Partial<EditorialOpportunity>){setItem(current=>current?{...current,...patch}:current);setMessage('')}
  function updateSection(index:number,next:EditorialSection){if(!item)return;update({sections:item.sections.map((s,i)=>i===index?next:s)})}
  function moveSection(index:number,direction:number){if(!item)return;const sections=[...item.sections];const to=index+direction;if(to<0||to>=sections.length)return;[sections[index],sections[to]]=[sections[to],sections[index]];update({sections:sections.map((s,i)=>({...s,position:i}))})}
  function addSection(){if(!item)return;update({sections:[...item.sections,{title:'Nova seção',position:item.sections.length,fields:[]}]})}
  function addField(sectionIndex:number){if(!item)return;const sections=[...item.sections];sections[sectionIndex]={...sections[sectionIndex],fields:[...sections[sectionIndex].fields,{name:'Nova informação',type:'SHORT_TEXT',value:'',position:sections[sectionIndex].fields.length}]};update({sections})}
  function updateField(sectionIndex:number,fieldIndex:number,patch:Partial<EditorialField>){if(!item)return;const sections=[...item.sections];const section=sections[sectionIndex];sections[sectionIndex]={...section,fields:section.fields.map((f,i)=>i===fieldIndex?{...f,...patch}:f)};update({sections})}
  function moveField(sectionIndex:number,fieldIndex:number,direction:number){if(!item)return;const sections=[...item.sections];const fields=[...sections[sectionIndex].fields];const to=fieldIndex+direction;if(to<0||to>=fields.length)return;[fields[fieldIndex],fields[to]]=[fields[to],fields[fieldIndex]];sections[sectionIndex]={...sections[sectionIndex],fields:fields.map((field,position)=>({...field,position}))};update({sections})}
  if(session===null)return <Shell admin><main className="editorial-container">Carregando sessão…</main></Shell>
  if(!session)return <Shell admin><AdminLogin onLogin={login}/></Shell>
  if(!routeId)return <Shell admin><main className="editorial-container editorial-admin"><div className="editorial-heading"><div><p className="editorial-eyebrow">CENTRAL DE OPORTUNIDADES</p><h1>O que precisa entrar no catálogo?</h1><p>Cadastre e publique oportunidades em uma página clara para seus visitantes.</p></div><button className="editorial-button editorial-button--primary" disabled={busy} onClick={()=>void create()}>＋ Nova oportunidade</button></div>
    <label className="editorial-search">Buscar por título ou instituição<input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Buscar oportunidade"/></label>{error&&<p role="alert">{error}</p>}
    {items.map(row=><article className="editorial-list-item" key={row.id}><p className="editorial-eyebrow">{row.editorialState==='PUBLISHED'?'Publicada':'Rascunho'}</p><h2>{row.title||'Sem título'}</h2><p>{row.institution||'Instituição não informada'} · Prazo: {formatDate(row.deadline)}</p><button className="editorial-text-link" onClick={()=>navigate(`/admin/manual/${row.id}`)}>Editar oportunidade →</button></article>)}
    {items.length===0&&!error&&<p className="editorial-empty">Ainda não há oportunidades cadastradas. Crie a primeira para começar.</p>}</main></Shell>
  if(routeId==='new'&&!item)return <Shell admin><main className="editorial-container"><p>Preparando oportunidade…</p></main></Shell>
  if(!item)return <Shell admin><main className="editorial-container">{error?<p role="alert">{error}</p>:'Carregando oportunidade…'}</main></Shell>
  return <Shell admin><main className="editorial-container editorial-editor"><button className="editorial-back" onClick={()=>navigate('/admin')}>← Voltar às oportunidades</button>
    <div className="editorial-heading"><div><p className="editorial-eyebrow">{item.editorialState==='PUBLISHED'?'PUBLICADA':'RASCUNHO'}</p><h1>{item.title||'Nova oportunidade'}</h1><p>Organize as informações como fizer sentido para esta oportunidade.</p></div><div className="editorial-actions">
      <button className="editorial-button editorial-button--secondary" onClick={()=>setPreview(!preview)}>{preview?'Voltar à edição':'Pré-visualizar'}</button>
      <button className="editorial-button editorial-button--primary" disabled={busy||!dirty} onClick={()=>void save()}>Salvar rascunho</button>
      {item.editorialState==='PUBLISHED'?<><button className="editorial-button editorial-button--primary" disabled={busy} onClick={()=>void publish()}>Atualizar publicação</button><button className="editorial-button editorial-button--secondary" disabled={busy} onClick={()=>void unpublish()}>Retirar da publicação</button></>:<button className="editorial-button editorial-button--primary" disabled={busy} onClick={()=>void publish()}>Publicar</button>}
    </div></div>{error&&<p role="alert" className="editorial-message editorial-message--error">{error}</p>}{message&&<p role="status" className="editorial-message">{message}</p>}
    {preview?<EditorialPreview item={item}/>:<div className="editorial-form-grid"><section className="editorial-panel"><h2>O essencial da oportunidade</h2>
      <label>Título<input value={item.title} onChange={e=>update({title:e.target.value})}/></label><label>Instituição responsável<input value={item.institution} onChange={e=>update({institution:e.target.value})}/></label>
      <label>Resumo<textarea rows={4} maxLength={2000} value={item.summary} onChange={e=>update({summary:e.target.value})}/></label>
      <div className="editorial-two-col"><label>Prazo<input type="date" value={item.deadline??''} onChange={e=>update({deadline:e.target.value||null})}/></label><label>Situação<select value={item.status} onChange={e=>update({status:e.target.value})}>{['Inscrições abertas','Em breve','Encerrada','Suspensa'].map(s=><option key={s}>{s}</option>)}</select></label></div>
      <label>URL oficial<input type="url" value={item.officialUrl??''} placeholder="https://" onChange={e=>update({officialUrl:e.target.value||null})}/></label>
      </section><section className="editorial-panel"><div className="editorial-section-heading"><div><h2>Conteúdo da página</h2><p>Adicione as seções necessárias; a ordem será mantida na página pública.</p></div><button className="editorial-button editorial-button--secondary" onClick={addSection}>＋ Adicionar seção</button></div>
        {item.sections.map((section,si)=><section className="editorial-section-editor" key={si}><div className="editorial-section-heading"><input aria-label={`Título da seção ${si+1}`} value={section.title} onChange={e=>updateSection(si,{...section,title:e.target.value})}/><div><button aria-label="Mover seção para cima" disabled={si===0} onClick={()=>moveSection(si,-1)}>↑</button><button aria-label="Mover seção para baixo" disabled={si===item.sections.length-1} onClick={()=>moveSection(si,1)}>↓</button><button aria-label="Remover seção" onClick={()=>update({sections:item.sections.filter((_,i)=>i!==si).map((s,i)=>({...s,position:i}))})}>×</button></div></div>
          {section.fields.map((field,fi)=><div className="editorial-field-editor" key={fi}><label>Nome da informação<input value={field.name} onChange={e=>updateField(si,fi,{name:e.target.value})}/></label><label>Tipo<select value={field.type} onChange={e=>updateField(si,fi,{type:e.target.value as EditorialFieldType,value:e.target.value==='LIST'?[]:''})}>{TYPES.map(type=><option key={type.value} value={type.value}>{type.label}</option>)}</select></label>
            <label>Valor{field.type==='LONG_TEXT'?<textarea rows={3} value={String(field.value??'')} onChange={e=>updateField(si,fi,{value:e.target.value})}/>:field.type==='LIST'?<textarea rows={3} placeholder="Um item por linha" value={Array.isArray(field.value)?field.value.join('\n'):''} onChange={e=>updateField(si,fi,{value:e.target.value.split('\n')})}/>:<input type={field.type==='DATE'?'date':field.type==='CURRENCY'?'number':field.type==='LINK'?'url':'text'} step={field.type==='CURRENCY'?'0.01':undefined} value={String(field.value??'')} onChange={e=>updateField(si,fi,{value:e.target.value})}/>}</label>
            <div className="editorial-field-actions"><button aria-label="Mover informação para cima" disabled={fi===0} onClick={()=>moveField(si,fi,-1)}>↑</button><button aria-label="Mover informação para baixo" disabled={fi===section.fields.length-1} onClick={()=>moveField(si,fi,1)}>↓</button><button className="editorial-text-link" onClick={()=>updateSection(si,{...section,fields:section.fields.filter((_,i)=>i!==fi).map((f,i)=>({...f,position:i}))})}>Remover informação</button></div></div>)}
          <button className="editorial-text-link" onClick={()=>addField(si)}>＋ Adicionar informação</button></section>)}
        {item.sections.length===0&&<p className="editorial-empty">Comece com uma seção, como “Quem pode participar” ou “Apoio financeiro”.</p>}
      </section></div>}
    <p className="editorial-small">Salvar mantém as alterações em rascunho. A publicação só muda quando você escolhe Publicar.</p>
  </main></Shell>
}

function EditorialPreview({item}:{item:EditorialOpportunity}) { return <section className="editorial-preview"><p className="editorial-preview-label">PRÉVIA · ainda não é uma publicação pública</p><EditorialArticle item={item}/></section> }
function EditorialArticle({item}:{item:EditorialOpportunity}) { return <article><header className="editorial-detail-header"><p className="editorial-eyebrow">{item.institution||'Instituição responsável'}</p><h1>{item.title||'Título da oportunidade'}</h1><p className="editorial-lead">{item.summary||'Adicione um resumo para apresentar a oportunidade.'}</p><dl className="editorial-facts"><div><dt>Instituição</dt><dd>{item.institution||'Não informada'}</dd></div><div><dt>Prazo</dt><dd>{formatDate(item.deadline)}</dd></div><div><dt>Situação</dt><dd>{item.status}</dd></div></dl>{safeUrl(item.officialUrl)&&<a href={safeUrl(item.officialUrl)!} target="_blank" rel="noopener noreferrer">Consultar página oficial ↗</a>}</header>
  <div className="editorial-sections">{item.sections.map((section,si)=><section key={`${si}-${section.title}`}><h2>{section.title}</h2>{section.fields.map((field,fi)=><div className="editorial-field" key={`${fi}-${field.name}`}><h3>{field.name}</h3>{field.type==='LIST'&&Array.isArray(field.value)?<ul>{field.value.map((v,i)=><li key={i}>{v}</li>)}</ul>:field.type==='LINK'&&safeUrl(String(field.value??''))?<a href={safeUrl(String(field.value))!} target="_blank" rel="noopener noreferrer">{String(field.value)} ↗</a>:<p>{formatValue(field)}</p>}</div>)}</section>)}</div></article> }
