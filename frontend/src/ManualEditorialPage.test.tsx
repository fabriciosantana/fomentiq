import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function response(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response
}
function renderAt(path: string) {
  window.history.replaceState({}, '', path)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><App /></QueryClientProvider>)
}
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('manual editorial experience', () => {
  it('shows only API-published opportunities and opens an editorial detail', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://localhost').pathname
      if (path === '/api/editorial/opportunities') return response({ content: [
        { id: 'published-id', title: 'Apoio à inovação', institution: 'Instituto de Demonstração',
          summary: 'Apoio a projetos de inovação.', deadline: '2027-04-30', status: 'Inscrições abertas',
          officialUrl: 'https://example.org/edital', sections: [{ title: 'Quem pode participar', position: 0,
            fields: [{ name: 'Público', type: 'LIST', value: ['Empresas brasileiras'], position: 0 }] }] },
      ], page: 0, size: 20, totalElements: 1 })
      if (path.endsWith('/published-id')) return response({ id: 'published-id', title: 'Apoio à inovação',
        institution: 'Instituto de Demonstração', summary: 'Apoio a projetos de inovação.', deadline: '2027-04-30',
        status: 'Inscrições abertas', officialUrl: 'https://example.org/edital', sections: [{ title: 'Quem pode participar',
          position: 0, fields: [{ name: 'Público', type: 'LIST', value: ['Empresas brasileiras'], position: 0 }] }] })
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderAt('/')
    expect(await screen.findByRole('heading', { name: 'Apoio à inovação' })).toBeInTheDocument()
    expect(screen.getByText(/Instituto de Demonstração/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: /Conhecer oportunidade/ }))
    expect(await screen.findByRole('heading', { name: 'Quem pode participar' })).toBeInTheDocument()
    expect(screen.getByText('Empresas brasileiras')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Consultar página oficial ↗' })).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('keeps manual administration behind the session and CSRF login', async () => {
    const requests: Array<{ path: string; init?: RequestInit }> = []
    let csrfCount = 0
    let sessionCount = 0
    let current = { id: 'manual-1', title: '', institution: '', summary: '', deadline: null,
      status: 'Inscrições abertas', officialUrl: '', editorialState: 'DRAFT', sections: [], rowVersion: 0 }
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input), 'http://localhost').pathname
      requests.push({ path, init })
      if (path === '/api/admin/csrf') return response({ headerName: 'X-XSRF-TOKEN', token: `csrf-${++csrfCount}` })
      if (path === '/api/admin/session') return ++sessionCount === 1
        ? response({ message: 'unauthenticated' }, 401) : response({ username: 'editor' })
      if (path === '/api/admin/login') return response(null, 204)
      if (path === '/api/admin/manual-opportunities' && init?.method === 'POST') return response(current)
      if (path === '/api/admin/manual-opportunities' && !init?.method) return response({ content: [], page: 0, size: 100, totalElements: 0 })
      if (path === '/api/admin/manual-opportunities/manual-1' && init?.method === 'PUT') {
        const requestBody = JSON.parse(String(init.body)) as { expectedVersion: number; content: typeof current }
        current = { ...requestBody.content, id: 'manual-1', editorialState: 'DRAFT', rowVersion: 1 }
        return response(current)
      }
      if (path.endsWith('/manual-1/publish') && init?.method === 'POST') {
        current = { ...current, editorialState: 'PUBLISHED', rowVersion: current.rowVersion + 1 }
        return response(current)
      }
      if (path.endsWith('/manual-1/unpublish') && init?.method === 'POST') {
        current = { ...current, editorialState: 'DRAFT', rowVersion: current.rowVersion + 1 }
        return response(current)
      }
      if (path === '/api/admin/manual-opportunities/manual-1') return response(current)
      return response({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderAt('/admin')
    await screen.findByLabelText('Usuário')
    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'editor' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'demo-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('heading', { name: 'O que precisa entrar no catálogo?' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Nova oportunidade/ }))
    expect(await screen.findByRole('heading', { name: 'Nova oportunidade' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Edital de demonstração' } })
    fireEvent.change(screen.getByLabelText('Instituição responsável'), { target: { value: 'Instituto Teste' } })
    fireEvent.change(screen.getByLabelText('Resumo'), { target: { value: 'Apoio demonstrativo.' } })
    const confirmMock = vi.fn(() => false)
    vi.stubGlobal('confirm', confirmMock)
    fireEvent.click(screen.getByRole('link', { name: 'Catálogo' }))
    expect(confirmMock).toHaveBeenCalledWith('Há alterações não salvas. Sair mesmo assim?')
    expect(await screen.findByRole('heading', { name: 'Edital de demonstração' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '＋ Adicionar seção' }))
    fireEvent.change(screen.getByLabelText('Título da seção 1'), { target: { value: 'Participação' } })
    fireEvent.click(screen.getByRole('button', { name: '＋ Adicionar informação' }))
    fireEvent.change(screen.getByLabelText('Nome da informação'), { target: { value: 'Público elegível' } })
    fireEvent.change(screen.getByLabelText('Valor'), { target: { value: 'Empresas' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar rascunho' }))
    await waitFor(() => expect(requests.some(request => request.path.endsWith('/manual-1')
      && request.init?.method === 'PUT')).toBeTruthy())
    const saveRequest = requests.find(request => request.init?.method === 'PUT')
    expect(saveRequest?.init?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-3' })
    expect(screen.getByRole('status')).toHaveTextContent('Rascunho salvo')
    expect(fetchMock.mock.calls.some(call => String(call[0]).startsWith('/api/opportunities'))).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Oportunidade publicada')
    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Alteração não salva' } })
    fireEvent.click(screen.getByRole('button', { name: 'Retirar da publicação' }))
    expect(confirmMock).toHaveBeenLastCalledWith('Há alterações não salvas. Retirar a publicação descartará essas alterações. Continuar?')
    expect(requests.some(request => request.path.endsWith('/manual-1/unpublish'))).toBe(false)
    confirmMock.mockReturnValue(true)
    fireEvent.click(screen.getByRole('button', { name: 'Retirar da publicação' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Publicação retirada')
    expect(requests.some(request => request.path.endsWith('/manual-1/unpublish'))).toBe(true)
  })
})
