import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { useMemo } from 'react'
import OpportunityCatalogPage from './OpportunityCatalogPage'
import OpportunityDetailPage from './OpportunityDetailPage'
import AdminCurationPage from './AdminCurationPage'
import { EditorialCatalogPage, EditorialOpportunityPage, ManualOpportunityAdminPage } from './ManualEditorialPage'

function App() {
  const router = useMemo(() => createBrowserRouter([
    { path: '/', element: <EditorialCatalogPage /> },
    { path: '/catalog/:id', element: <EditorialOpportunityPage /> },
    { path: '/radar', element: <OpportunityCatalogPage /> },
    { path: '/opportunities/:callId', element: <OpportunityDetailPage /> },
    { path: '/admin', element: <ManualOpportunityAdminPage /> },
    { path: '/admin/manual/:id', element: <ManualOpportunityAdminPage /> },
    { path: '/admin/curation', element: <AdminCurationPage /> },
    { path: '/admin/curation/opportunities/:callId', element: <AdminCurationPage /> },
    { path: '/admin/opportunities/:callId', element: <AdminCurationPage /> },
    { path: '*', element: <EditorialCatalogPage /> },
  ]), [])
  return <RouterProvider router={router} />
}

export default App
