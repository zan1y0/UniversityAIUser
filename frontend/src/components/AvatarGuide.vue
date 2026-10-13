<script setup lang="ts">
withDefaults(defineProps<{ mood?: string; compact?: boolean }>(), { mood: '温柔', compact: false });
</script>
<template>
  <div class="avatar-guide" :class="{ compact }">
    <svg viewBox="0 0 260 280" role="img" :aria-label="`${mood}模式数字人形象`">
      <defs>
        <linearGradient id="avatar-bg" x1="0" y1="0" x2="1" y2="1">
          <stop stop-color="#d8e9ff" />
          <stop offset="1" stop-color="#eff7fb" />
        </linearGradient>
      </defs>
      <circle cx="130" cy="125" r="99" fill="url(#avatar-bg)" />
      <circle cx="211" cy="68" r="8" fill="#8aabfa" />
      <circle cx="48" cy="174" r="5" fill="#a5cbc5" />
      <path d="M65 260Q65 196 102 190H157Q197 196 197 260" fill="#627ed8" />
      <path d="M101 187L129 214L158 187L154 231H105Z" fill="#fff" />
      <rect x="113" y="155" width="34" height="41" rx="15" fill="#f4cdb5" />
      <path
        d="M75 104Q73 46 129 46Q183 46 185 104L177 175Q167 195 153 191H102Q78 187 77 164Z"
        fill="#25314b"
      />
      <ellipse cx="130" cy="121" rx="45" ry="56" fill="#ffdfc6" />
      <path d="M85 102Q88 51 139 56Q177 61 177 105Q144 92 132 75Q123 94 85 102" fill="#25314b" />
      <path
        d="M98 118Q105 113 112 118M146 118Q154 113 161 118"
        fill="none"
        stroke="#3d3c43"
        stroke-width="3"
        stroke-linecap="round"
      />
      <path
        d="M118 145Q130 155 143 145"
        fill="none"
        stroke="#bb7374"
        stroke-width="3"
        stroke-linecap="round"
      />
      <ellipse cx="99" cy="133" rx="8" ry="4" fill="#f5b9b2" />
      <ellipse cx="158" cy="133" rx="8" ry="4" fill="#f5b9b2" />
      <path d="M65 260H197" stroke="#aec4ef" stroke-width="3" />
      <g class="avatar-hand">
        <path d="M185 231L210 169" stroke="#627ed8" stroke-width="20" stroke-linecap="round" />
        <path d="M210 169L214 147" stroke="#ffdfc6" stroke-width="14" stroke-linecap="round" />
      </g>
    </svg>
    <span class="avatar-label"><span class="status-dot"></span>研小途 · {{ mood }}陪伴</span>
  </div>
</template>
