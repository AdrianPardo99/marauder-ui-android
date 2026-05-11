<template>
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div
      class="bg-zinc-900 rounded-lg border border-zinc-800 shadow-2xl p-6 max-w-2xl w-full mx-4 max-h-[90vh] flex flex-col text-zinc-300">
      <!-- Header - Fixed -->
      <div class="flex justify-between items-center mb-4">
        <div>
          <h2 class="text-xl font-bold">{{ workflow.name }}</h2>
          <p class="text-gray-600 text-sm mt-1">{{ workflow.description }}</p>
        </div>
        <button @click="$emit('close')" class="p-2 hover:bg-zinc-800 rounded flex-shrink-0 text-zinc-500 hover:text-zinc-300">
          ✕
        </button>
      </div>

      <!-- Steps - Scrollable -->
      <div class="flex-1 overflow-y-auto min-h-0 mb-6">
        <div class="space-y-4">
          <div v-for="(step, index) in workflow.steps" :key="index"
            class="flex items-start space-x-4 p-4 bg-zinc-950/50 rounded-lg border border-zinc-800">
            <div
              class="w-6 h-6 rounded-full bg-emerald-900/30 text-emerald-400 flex-shrink-0 flex items-center justify-center text-sm font-bold border border-emerald-800/50">
              {{ index + 1 }}
            </div>
            <div class="flex-1 space-y-2">
              <p class="text-sm">{{ step.description }}</p>

              <!-- Command preview -->
              <div class="font-mono text-sm bg-black p-2 rounded break-all border border-zinc-800 text-emerald-500">
                {{ getFormattedCommand(step, index) }}
              </div>

              <!-- Input fields -->
              <div v-if="step.requiresInput" class="space-y-2">
                <!-- First input -->
                <div class="space-y-1">
                  <label class="text-sm font-medium text-zinc-300">{{ step.inputLabel }}</label>
                  <input v-model="inputs[index]" type="text" :placeholder="step.placeholder"
                    class="input-field w-full">
                  <p v-if="step.help" class="text-xs text-zinc-500">{{ step.help }}</p>
                </div>

                <!-- Second input if required -->
                <div v-if="step.requiresSecondInput" class="space-y-1">
                  <label class="text-sm font-medium text-zinc-300">{{ step.secondInputLabel }}</label>
                  <input v-model="secondInputs[index]" type="text" :placeholder="step.secondPlaceholder"
                    class="input-field w-full">
                </div>
              </div>

              <div v-if="step.requiresSerialPayload" class="space-y-1">
                <label class="text-sm font-medium text-zinc-300">
                  {{ step.serialPayloadLabel || 'Serial Payload' }}
                </label>
                <textarea
                  v-model="serialPayloads[index]"
                  :placeholder="step.serialPayloadPlaceholder || 'Paste payload here...'"
                  rows="8"
                  class="input-field w-full font-mono"
                />
                <p class="text-xs text-zinc-500">
                  This payload is sent as raw serial data immediately after the command.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Actions - Fixed at bottom -->
      <div class="flex justify-between items-center pt-4 border-t border-zinc-800">
        <!-- Warning for destructive workflows -->
        <div v-if="isDestructiveWorkflow" class="text-sm text-orange-600">
          ⚠️ This workflow may disrupt network connectivity
        </div>

        <div class="flex space-x-4">
          <button @click="$emit('close')"
            class="btn">
            Cancel
          </button>
          <button @click="executeWorkflow" :disabled="!isValid"
            class="btn btn-primary">
            Execute Workflow
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Custom scrollbar styling */
.overflow-y-auto {
  scrollbar-width: thin;
  scrollbar-color: #CBD5E1 transparent;
}

.overflow-y-auto::-webkit-scrollbar {
  width: 6px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: transparent;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background-color: #CBD5E1;
  border-radius: 3px;
}

/* Smooth scrolling */
.overflow-y-auto {
  scroll-behavior: smooth;
}
</style>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  workflow: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['close', 'execute'])

const inputs = ref({})
const secondInputs = ref({})
const serialPayloads = ref({})

const isDestructiveWorkflow = computed(() => {
  return props.workflow.id.includes('deauth') ||
    props.workflow.id.includes('attack') ||
    props.workflow.id.includes('spam')
})

const isValid = computed(() => {
  return props.workflow.steps.every((step, index) => {
    if (step.requiresInput) {
      if (!inputs.value[index]?.trim()) return false
      if (step.requiresSecondInput && !secondInputs.value[index]?.trim()) return false
    }
    if (step.requiresSerialPayload && !serialPayloads.value[index]?.trim()) return false
    return true
  })
})

const resolveCommandForStep = (step, index, preview = false) => {
  if (!step.requiresInput) return step.command

  if (step.command.includes('{type}') && step.command.includes('{value}')) {
    const parts = (inputs.value[index] || '').trim().split(/\s+/)
    const type = parts[0]
    const value = parts.slice(1).join(' ')
    let command = step.command
    command = command.replace('{type}', type || (preview ? '{type}' : ''))
    command = command.replace('{value}', value || (preview ? '{value}' : ''))
    return command
  }

  let placeholderIndex = 0
  return step.command.replace(/{([^}]+)}/g, (match) => {
    placeholderIndex += 1

    if (placeholderIndex === 1) {
      const value = inputs.value[index]?.trim()
      return value || (preview ? match : '')
    }

    if (placeholderIndex === 2) {
      const value = secondInputs.value[index]?.trim()
      return value || (preview ? match : '')
    }

    return preview ? match : ''
  })
}

const getFormattedCommand = (step, index) => {
  return resolveCommandForStep(step, index, true)
}

const executeWorkflow = () => {
  const steps = props.workflow.steps.map((step, index) => {
    return {
      command: resolveCommandForStep(step, index, false),
      serialPayload: step.requiresSerialPayload ? serialPayloads.value[index]?.trim() : undefined,
      payloadDelayMs: step.payloadDelayMs ?? 200
    }
  })

  emit('execute', steps)
}
</script>
