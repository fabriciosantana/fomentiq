import { BrowserRouter, Route, Routes } from 'react-router-dom'
import OpportunityCatalogPage from './OpportunityCatalogPage'
import OpportunityDetailPage from './OpportunityDetailPage'
import AdminCurationPage from './AdminCurationPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<OpportunityCatalogPage />} />
        <Route path="/opportunities/:callId" element={<OpportunityDetailPage />} />
        <Route path="/admin" element={<AdminCurationPage />} />
        <Route path="/admin/opportunities/:callId" element={<AdminCurationPage />} />
        <Route path="*" element={<OpportunityCatalogPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
