import axios from 'axios'

const api = axios.create({ baseURL: '/api/v1' })

export const startSimulation = (courseId, botCount, nickname) =>
  api.post('/simulation/start', { courseId, botCount, nickname }).then(r => r.data.data)

export const getStatus = (simulationId) =>
  api.get(`/simulation/status/${simulationId}`).then(r => r.data.data)

export const stopSimulation = (simulationId) =>
  api.post('/simulation/stop', { simulationId }).then(r => r.data.data)

export const getCourses = () =>
  api.get('/courses/search').then(r => r.data.data)

export const createSseConnection = (simulationId, onMessage, onComplete, onError) => {
  const es = new EventSource(`/api/v1/simulation/live/${simulationId}`)

  es.addEventListener('status', (e) => {
    onMessage(JSON.parse(e.data))
  })

  es.addEventListener('complete', () => {
    onComplete()
    es.close()
  })

  es.addEventListener('stopped', () => {
    onComplete()
    es.close()
  })

  es.onerror = (e) => {
    onError(e)
    es.close()
  }

  return es
}
