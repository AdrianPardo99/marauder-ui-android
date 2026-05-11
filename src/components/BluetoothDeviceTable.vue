<template>
  <div class="h-full flex flex-col">
    <div class="flex flex-col gap-4 mb-4">
      <div class="flex justify-between items-center">
        <div class="flex items-center space-x-2">
          <h2 class="text-xl font-bold text-zinc-100">Bluetooth Devices</h2>
          <span class="text-sm text-zinc-500">({{ devices.size }} devices)</span>
        </div>
        <div class="flex items-center space-x-2">
          <button @click="refreshList"
            class="btn btn-primary"
            :disabled="!isConnected">
            Refresh
          </button>
          <button @click="clearTable"
            class="btn btn-accent">
            Clear
          </button>
        </div>
      </div>
      <div class="flex space-x-4">
        <div class="flex-1">
          <input type="text" v-model="search" placeholder="Search by Name, MAC, or Vendor..."
            class="input-field w-full">
        </div>
        <select v-model="sortBy"
          class="input-field">
          <option value="rssi">Sort by Signal</option>
          <option value="name">Sort by Name</option>
        </select>
      </div>
    </div>
    <div class="flex-1 min-h-0 overflow-auto border border-zinc-800 rounded bg-zinc-950/50">
      <table class="w-full text-sm">
        <thead class="bg-zinc-900 sticky top-0 z-10">
          <tr>
            <th class="px-2 py-2 text-left border-b border-zinc-800 font-bold w-12 text-zinc-400">#</th>
            <th class="px-2 py-2 text-left border-b border-zinc-800 font-bold text-zinc-400">MAC</th>
            <th class="px-2 py-2 text-left border-b border-zinc-800 font-bold text-zinc-400">Name</th>
            <th class="px-2 py-2 text-left border-b border-zinc-800 w-16 font-bold text-zinc-400">RSSI</th>
            <th class="px-2 py-2 text-left border-b border-zinc-800 w-24 font-bold text-zinc-400">Vendor</th>
            <th class="px-2 py-2 text-left border-b border-zinc-800 w-24 font-bold text-zinc-400">Last Seen</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="device in sortedDevices" :key="device.mac || device.name">
            <tr class="hover:bg-zinc-800/50 border-b border-zinc-900 cursor-pointer">
              <td class="px-2 py-1 font-mono text-zinc-500">{{ device.index }}</td>
              <td class="px-2 py-1 font-mono text-zinc-400">{{ device.mac || '-' }}</td>
              <td class="px-2 py-1 font-medium text-zinc-200">{{ device.name || '-' }}</td>
              <td class="px-2 py-1 text-emerald-500 font-mono">{{ device.rssi || 'N/A' }}</td>
              <td class="px-2 py-1 text-zinc-500 italic text-xs">{{ device.vendor || 'Unknown' }}</td>
              <td class="px-2 py-1 text-zinc-600 text-xs text-right">{{ formatLastSeen(device.lastSeen) }}</td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useSerialConnection } from '../utils/serialConnection'

const { sendCommand, isConnected, terminalOutput } = useSerialConnection()
const devices = ref(new Map())
const search = ref('')
const sortBy = ref('rssi')
const lastProcessedIndex = ref(0)

const sortedDevices = computed(() => {
  let arr = Array.from(devices.value.values())
  if (search.value) {
    const searchLower = search.value.toLowerCase()
    arr = arr.filter(device =>
      device.name?.toLowerCase().includes(searchLower) ||
      device.mac?.toLowerCase().includes(searchLower) ||
      device.vendor?.toLowerCase().includes(searchLower)
    )
  }
  return arr.sort((a, b) => {
    switch (sortBy.value) {
      case 'rssi':
        return (b.rssi ?? -999) - (a.rssi ?? -999) || a.name.localeCompare(b.name)
      case 'name':
        return a.name.localeCompare(b.name)
      default:
        return 0
    }
  })
})

const formatLastSeen = (date) => {
  if (!date) return 'N/A'
  const seconds = Math.floor((new Date() - date) / 1000)
  if (seconds < 60) return `${seconds}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`
  return `${Math.floor(seconds / 3600)}h`
}

const clearTable = () => {
  devices.value = new Map();
}

const refreshList = async () => {
  if (isConnected.value) {
    await sendCommand('sniffbt') // Comando para escanear y mostrar dispositivos Bluetooth
  }
}

watch(() => terminalOutput.value, (newLines) => {
  if (newLines.length <= lastProcessedIndex.value) return

  const linesToProcess = newLines.slice(lastProcessedIndex.value)
  lastProcessedIndex.value = newLines.length

  const newDevices = new Map(devices.value);
  let changed = false;

  linesToProcess.forEach(line => {
    const plainLine = line.replace(/<[^>]+>/g, '').trim();
    // Firmware sends multiple devices concatenated: "-89 Device: mac-60 Device: name..."
    const matches = plainLine.matchAll(/(-?\d+)\s+Device:\s*((?:(?!-?\d+\s+Device:).)+)/g);
    for (const match of matches) {
      const rssi = parseInt(match[1]);
      const device = match[2].trim();
      const isMac = /^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$/.test(device);
      let mac = '-';
      let name = '-';
      let key = device;

      if (isMac) {
        mac = device;
        const existing = Array.from(newDevices.values()).find(d => d.mac === mac);
        if (existing) {
          name = existing.name || '-';
          key = existing.mac || mac;
        }
      } else {
        name = device;
        const existing = Array.from(newDevices.values()).find(d => d.name === name);
        if (existing) {
          mac = existing.mac || '-';
          key = existing.name || name;
        }
      }

      newDevices.set(key, {
        index: newDevices.size + 1,
        mac,
        name,
        rssi,
        lastSeen: new Date()
      });
      changed = true;
    }
  });

  if (changed) {
    // Re-index
    let idx = 1;
    for (const [key, dev] of newDevices) {
      dev.index = idx++;
    }
    devices.value = newDevices;
  }
}, { deep: true });

const cleanup = () => {
  const now = new Date()
  for (const [key, device] of devices.value.entries()) {
    if ((now - device.lastSeen) > 5 * 60 * 1000) {
      devices.value.delete(key)
    }
  }
}

onMounted(() => {
  const interval = setInterval(cleanup, 30000)
  onUnmounted(() => clearInterval(interval))
})
</script>
