const url = process.argv[2]

if (!url) {
  throw new Error('Usage: node WebSocketTicketSmoke.mjs <websocket-url>')
}

const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

function connect(target) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(target)
    const timeout = setTimeout(() => reject(new Error('WebSocket connection timed out')), 3000)
    socket.addEventListener('open', () => {
      clearTimeout(timeout)
      resolve(socket)
    }, { once: true })
    socket.addEventListener('error', () => {
      clearTimeout(timeout)
      reject(new Error('WebSocket connection failed'))
    }, { once: true })
  })
}

const first = await connect(url)
await wait(300)
if (first.readyState !== WebSocket.OPEN) {
  throw new Error('Fresh ticket did not keep the WebSocket connection open')
}
first.close()

const reused = new WebSocket(url)
await new Promise((resolve, reject) => {
  const timeout = setTimeout(() => reject(new Error('Reused ticket remained connected')), 3000)
  reused.addEventListener('close', () => {
    clearTimeout(timeout)
    resolve()
  }, { once: true })
  reused.addEventListener('error', () => {
    clearTimeout(timeout)
    resolve()
  }, { once: true })
})

console.log('WebSocket ticket accepted once and rejected on reuse')
