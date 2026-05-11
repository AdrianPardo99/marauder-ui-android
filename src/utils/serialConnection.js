import { ref, computed } from 'vue'
import { Capacitor, registerPlugin } from '@capacitor/core'

// Definición moderna del plugin MarauderSerial
const MarauderSerial = registerPlugin('MarauderSerial')

// Create singleton instance
const port = ref(null)
const reader = ref(null)
const isConnected = ref(false)
const terminalOutput = ref([])
const isDemoMode = ref(false)
const activeCommand = ref(null)
let buffer = ''
let keepReading = false
let nativeListener = null
let nativeErrorListener = null
let nativeConnectedListener = null

const continuousCommands = new Set([
  'scanap', 'scanall', 'sniffbeacon', 'sniffdeauth', 'sniffpmkid', 'sniffpwn', 'sniffraw',
  'sniffbt', 'sniffskim', 'sniffmultissid', 'sniffpinescan', 'sniffsae',
  'mactrack', 'packetcount', 'attack', 'sigmon', 'pingscan', 'portscan',
  'gpsdata', 'nmea', 'wardrive', 'btwardrive', 'gpspoi', 'karma'
])

const isContinuous = (command) => {
  const base = command.split(/\s+/)[0]
  if (base === 'gps' && command.includes('-t')) return true
  return continuousCommands.has(base)
}

export const useSerialConnection = () => {
  const connect = async () => {
    const isAndroid = Capacitor.isNativePlatform()

    console.log('Connect function called, platform:', Capacitor.getPlatform())
    
    try {
      if (isAndroid) {
        addToTerminal('🔌 Connecting to background service...', 'normal')
        
        // Limpiar listeners previos
        if (nativeListener) await nativeListener.remove()
        if (nativeErrorListener) await nativeErrorListener.remove()
        if (nativeConnectedListener) await nativeConnectedListener.remove()

        // Configurar listeners de eventos nativos
        nativeListener = await MarauderSerial.addListener('data', (data) => {
          if (data.data) handleIncomingData(data.data)
        })

        nativeErrorListener = await MarauderSerial.addListener('error', (err) => {
          addToTerminal(`✗ Serial Error: ${err.error}`, 'error')
          isConnected.value = false
        })

        nativeConnectedListener = await MarauderSerial.addListener('connected', () => {
          isConnected.value = true
          addToTerminal('✓ Marauder Connected (Background Service)', 'success')
        })

        // Iniciar conexión nativa
        await MarauderSerial.connect()
      } else {
        // Lógica para Navegador Desktop
        if (!navigator.serial) {
          addToTerminal('✗ Web Serial not supported on this browser', 'error')
          return
        }
        port.value = await navigator.serial.requestPort()
        await port.value.open({ baudRate: 115200 })
        isConnected.value = true
        keepReading = true
        addToTerminal('✓ Connected!', 'success')
        startReading()
      }
    } catch (error) {
      console.error('Connection error:', error)
      isConnected.value = false
      const msg = error.message || 'Unknown error'
      if (msg.includes('not implemented')) {
        addToTerminal('✗ Error: Marauder Engine not registered in Android', 'error')
      } else {
        addToTerminal(`✗ Connection failed: ${msg}`, 'error')
      }
    }
  }

  const disconnect = async () => {
    const isAndroid = Capacitor.isNativePlatform()

    try {
      if (isAndroid) {
        await MarauderSerial.disconnect()
        isConnected.value = false
        addToTerminal('✗ Disconnected', 'error')
      } else if (port.value) {
        keepReading = false
        if (reader.value) await reader.value.cancel()
        await port.value.close()
        port.value = null
        isConnected.value = false
        addToTerminal('✗ Disconnected', 'error')
      }
    } catch (error) {
      console.error('Disconnect error:', error)
    }
  }

  const handleIncomingData = (dataStr) => {
    buffer += dataStr
    const lines = buffer.split('\n')
    buffer = lines.pop()
    lines.forEach(line => {
      if (line.trim()) addToTerminal(line.trim())
    })
  }

  const startReading = async () => {
    const decoder = new TextDecoder()
    while (port.value && port.value.readable && keepReading) {
      try {
        reader.value = port.value.readable.getReader()
        while (true) {
          const { value, done } = await reader.value.read()
          if (done) break
          if (value) handleIncomingData(decoder.decode(value, { stream: true }))
        }
      } catch (error) {
        console.error('Read error:', error)
      } finally {
        if (reader.value) reader.value.releaseLock()
      }
    }
  }

  const writeToSerial = async (text) => {
    const isAndroid = Capacitor.isNativePlatform()

    if (isAndroid && isConnected.value) {
      try {
        await MarauderSerial.send({ data: text })
      } catch (e) {
        console.error('Send error:', e)
      }
    } else if (port.value) {
      const writer = port.value.writable.getWriter()
      const encoder = new TextEncoder()
      await writer.write(encoder.encode(text))
      writer.releaseLock()
    }
  }

  const sendCommand = async (command) => {
    if (isDemoMode.value) {
      addToTerminal(`> ${command}`, 'command')
      return
    }
    if (!command || !isConnected.value) return
    try {
      await writeToSerial(command + '\n')
      addToTerminal(`> ${command}`, 'command')
      if (isContinuous(command)) activeCommand.value = command
    } catch (error) {
      addToTerminal(`✗ Failed to send: ${error.message}`, 'error')
    }
  }

  const sendRaw = async (rawText) => {
    if (!rawText || !isConnected.value) return
    try {
      await writeToSerial(rawText)
      addToTerminal(`> [raw] ${rawText}`, 'command')
    } catch (error) {
      console.error('Send raw error:', error)
    }
  }

  const addToTerminal = (text, type = 'normal') => {
    const classes = {
      normal: 'text-green-400',
      success: 'text-blue-400',
      error: 'text-red-500',
      command: 'text-yellow-400'
    }
    const lineClass = classes[type] || classes.normal
    terminalOutput.value = [...terminalOutput.value, `<span class="${lineClass}">${text}</span>`]
    if (terminalOutput.value.length > 500) {
      terminalOutput.value = terminalOutput.value.slice(-500)
    }
  }

  return {
    isConnected: computed(() => isConnected.value),
    isDemoMode,
    terminalOutput,
    connect,
    disconnect,
    activeCommand: computed(() => activeCommand.value),
    sendCommand,
    sendRaw
  }
}
