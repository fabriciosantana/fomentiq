import { BrowserRouter, Route, Routes } from 'react-router-dom'
import OpportunityCatalogPage from './OpportunityCatalogPage'
import OpportunityDetailPage from './OpportunityDetailPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<OpportunityCatalogPage />} />
        <Route path="/opportunities/:callId" element={<OpportunityDetailPage />} />
        <Route path="*" element={<OpportunityCatalogPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
