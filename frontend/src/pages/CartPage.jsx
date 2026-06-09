import { useParams, useLocation, useNavigate } from 'react-router-dom'
import AquaHeader from '../components/AquaHeader'
import './CartPage.css'

export default function CartPage() {
  const { simulationId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const state = location.state || {}
  const cart = state.cart || []

  const total = cart.reduce((sum, c) => sum + c.price, 0)

  const goBack = () => navigate(`/registration/${simulationId}`, { state })
  const goCheckout = () => navigate(`/checkout/${simulationId}`, { state })

  return (
    <>
      <AquaHeader
        step={1}
        cartCount={cart.length}
        onCartClick={() => {}}
      />

      <div className="cart-container">
        <div className="page-header">
          <h1 className="page-title">🛒 장바구니</h1>
          <p className="page-subtitle">선택하신 강좌를 확인하고 결제를 진행하세요</p>
        </div>

        <div className="cart-layout">
          <div className="cart-items-panel">
            <h2 className="section-title">
              강좌 목록 <span className="item-count">{cart.length}개</span>
            </h2>

            {cart.length === 0 ? (
              <div className="empty-cart">
                <div className="empty-icon">🛒</div>
                <p>장바구니가 비어있습니다</p>
                <button className="back-link" onClick={goBack}>강좌 둘러보기</button>
              </div>
            ) : (
              cart.map(course => (
                <div key={course.id} className="cart-item">
                  <div className="item-top">
                    <div>
                      <div className="item-center">{course.center}</div>
                      <div className="item-name">{course.name}</div>
                    </div>
                    <div className="item-price">{course.price.toLocaleString()}원</div>
                  </div>
                  <div className="item-details">
                    <span>📁 {course.category}</span>
                    <span>🎯 {course.target}</span>
                    <span>🕐 {course.time}</span>
                  </div>
                </div>
              ))
            )}
          </div>

          <div className="cart-summary">
            <h2 className="section-title">결제 정보</h2>
            <div className="summary-row">
              <span>강좌 수</span>
              <span>{cart.length}개</span>
            </div>
            <div className="summary-row">
              <span>총 수강료</span>
              <span>{total.toLocaleString()}원</span>
            </div>
            <div className="summary-row total">
              <span>최종 결제금액</span>
              <span className="total-value">{total.toLocaleString()}원</span>
            </div>
            <button
              className="checkout-btn"
              disabled={cart.length === 0}
              onClick={goCheckout}
            >
              결제하기
            </button>
            <button className="back-btn" onClick={goBack}>← 강좌 더 담기</button>
          </div>
        </div>
      </div>

    </>
  )
}
