import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import StartPage from './pages/StartPage'
import RegistrationPage from './pages/RegistrationPage'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'
import ResultPage from './pages/ResultPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<StartPage />} />
        <Route path="/registration" element={<RegistrationPage />} />
        <Route path="/cart/:simulationId" element={<CartPage />} />
        <Route path="/checkout/:simulationId" element={<CheckoutPage />} />
        <Route path="/result/:simulationId" element={<ResultPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
