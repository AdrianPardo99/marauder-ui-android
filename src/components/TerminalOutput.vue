<template>
    <div class="h-full flex flex-col">
      <div class="flex justify-between items-center mb-2">
        <h2 class="text-sm font-bold text-zinc-400 uppercase tracking-wider">Terminal Output</h2>
        <button 
          @click="clearOutput" 
          class="btn"
        >
          Clear
        </button>
      </div>
      
      <div 
        ref="outputContainer"
        class="flex-1 overflow-y-auto bg-black rounded border-2 border-black font-mono text-green-400 p-2 text-sm"
      >
        <div 
          v-for="(line, index) in outputLines"
          :key="index"
          class="terminal-line"
          v-html="line"
        ></div>
      </div>
    </div>
  </template>

  <script setup>
  import { ref, watch, onMounted } from 'vue'
  import { useSerialConnection } from '../utils/serialConnection'
  
  const { terminalOutput } = useSerialConnection()
  const outputContainer = ref(null)
  const outputLines = ref([])
  
  // Watch for changes in terminalOutput
  watch(() => terminalOutput.value, (newOutput) => {
    outputLines.value = [...newOutput]
    scrollToBottom()
  }, { deep: true })
  
  const scrollToBottom = () => {
    if (outputContainer.value) {
      setTimeout(() => {
        outputContainer.value.scrollTop = outputContainer.value.scrollHeight
      }, 0)
    }
  }
  
  const clearOutput = () => {
    outputLines.value = []
  }
  
  // Debug output
  watch(outputLines, (newLines) => {
    console.log('Terminal lines updated:', newLines)
  })
  </script>