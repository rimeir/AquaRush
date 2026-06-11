import axios from 'axios'

const api = axios.create({ baseURL: '/api/v1' })

export const startSimulation = (courseId, botCount, nickname, totalSeats, remainingSeats) =>
  api.post('/simulation/start', { courseId, botCount, nickname, totalSeats, remainingSeats }).then(r => r.data.data)

export const getStatus = (simulationId) =>
  api.get(`/simulation/status/${simulationId}`).then(r => r.data.data)

export const stopSimulation = (simulationId) =>
  api.post('/simulation/stop', { simulationId }).then(r => r.data.data)

export const getRandomCourse = () =>
  api.get('/courses/random').then(r => r.data.data)

export const getCourseDetail = (courseId) =>
  api.get(`/courses/${courseId}`).then(r => r.data.data)

export const getCenters = () =>
  api.get('/centers').then(r => r.data.data)

export const getCategories = () =>
  api.get('/categories').then(r => r.data.data)

export const getCourses = (params = {}) =>
  api.get('/courses/search', { params }).then(r => r.data.data)

export const reserveForUser = (simulationId) =>
  api.post(`/simulation/${simulationId}/reserve`).then(r => r.data.data)

export const enterAccessQueue = (botCount, arrivalVirtualMs, openVirtualMs) =>
  api.post('/access-queue/enter', { botCount, arrivalVirtualMs, openVirtualMs }).then(r => r.data.data)

export const getAccessQueueStatus = (queueToken) =>
  api.get(`/access-queue/status/${queueToken}`).then(r => r.data.data)

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
