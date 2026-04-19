<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const canvas = ref<HTMLCanvasElement | null>(null)
let raf = 0
let resizeHandler: (() => void) | null = null

interface Particle {
  arm: number
  r: number
  vr: number
  life: number
  maxLife: number
  size: number
  jitter: number
}

interface Dust {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  alpha: number
  twinkle: number
}

onMounted(() => {
  const c = canvas.value!
  const ctx = c.getContext('2d', { alpha: true })!
  let w = 0, h = 0, cx = 0, cy = 0
  let dpr = Math.min(window.devicePixelRatio || 1, 2)
  let angle = 0
  const particles: Particle[] = []
  const dust: Dust[] = []
  const ARMS = 4
  const SPIRAL = 1.6
  let isDark = false

  function detectTheme() {
    isDark = document.documentElement.classList.contains('dark')
  }

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2)
    w = window.innerWidth
    h = window.innerHeight
    c.width = w * dpr
    c.height = h * dpr
    c.style.width = w + 'px'
    c.style.height = h + 'px'
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    cx = w * 0.5
    cy = h * 0.5
  }

  function spawnParticle() {
    const arm = Math.floor(Math.random() * ARMS)
    const maxR = Math.hypot(w, h) * 0.28
    particles.push({
      arm,
      r: maxR + Math.random() * 40,
      vr: -(0.4 + Math.random() * 0.7),
      life: 0,
      maxLife: 280 + Math.random() * 160,
      size: 1.4 + Math.random() * 2.4,
      jitter: (Math.random() - 0.5) * 0.12
    })
  }

  function spawnDust() {
    dust.push({
      x: Math.random() * w,
      y: Math.random() * h,
      vx: (Math.random() - 0.5) * 0.15,
      vy: (Math.random() - 0.5) * 0.15 - 0.05,
      size: 0.4 + Math.random() * 1.1,
      alpha: 0.1 + Math.random() * 0.35,
      twinkle: Math.random() * Math.PI * 2
    })
  }

  for (let i = 0; i < 180; i++) spawnDust()

  function frame() {
    angle -= 0.0009
    ctx.clearRect(0, 0, w, h)

    // dust layer
    for (const d of dust) {
      d.x += d.vx
      d.y += d.vy
      d.twinkle += 0.02
      if (d.x < -10) d.x = w + 10
      if (d.x > w + 10) d.x = -10
      if (d.y < -10) d.y = h + 10
      if (d.y > h + 10) d.y = -10
      const a = d.alpha * (0.6 + Math.sin(d.twinkle) * 0.4)
      ctx.fillStyle = isDark
        ? `rgba(196, 181, 253, ${a})`
        : `rgba(124, 58, 237, ${a * 0.55})`
      ctx.beginPath()
      ctx.arc(d.x, d.y, d.size, 0, Math.PI * 2)
      ctx.fill()
    }

    // pinwheel particles (4 arms, strong spiral, inward flow)
    for (let i = 0; i < 10; i++) spawnParticle()

    for (let i = particles.length - 1; i >= 0; i--) {
      const p = particles[i]
      p.r += p.vr
      p.life++
      if (p.life > p.maxLife || p.r < 8) {
        particles.splice(i, 1)
        continue
      }
      const baseAngle = (Math.PI * 2 * p.arm) / ARMS + angle
      const spiral = baseAngle + Math.log(Math.max(p.r, 1)) * SPIRAL + p.jitter
      const x = cx + Math.cos(spiral) * p.r
      const y = cy + Math.sin(spiral) * p.r
      const lifeRatio = p.life / p.maxLife
      const fade = lifeRatio < 0.1
        ? lifeRatio / 0.1
        : 1 - (lifeRatio - 0.1) / 0.9
      const alpha = fade * 0.875
      ctx.fillStyle = isDark
        ? `rgba(196, 181, 253, ${alpha})`
        : `rgba(124, 58, 237, ${alpha * 0.75})`
      ctx.beginPath()
      ctx.arc(x, y, p.size, 0, Math.PI * 2)
      ctx.fill()
    }

    // soft vignette — 边缘暗角，中心透明，让粒子向中央汇聚的视觉自然形成
    const vg = ctx.createRadialGradient(cx, cy, Math.min(w, h) * 0.2, cx, cy, Math.max(w, h) * 0.7)
    vg.addColorStop(0, 'rgba(0, 0, 0, 0)')
    vg.addColorStop(1, isDark ? 'rgba(10, 10, 14, 0.45)' : 'rgba(245, 243, 255, 0.55)')
    ctx.fillStyle = vg
    ctx.fillRect(0, 0, w, h)

    raf = requestAnimationFrame(frame)
  }

  detectTheme()
  resize()
  resizeHandler = () => { resize() }
  window.addEventListener('resize', resizeHandler)

  const obs = new MutationObserver(detectTheme)
  obs.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })

  raf = requestAnimationFrame(frame)

  onUnmounted(() => {
    cancelAnimationFrame(raf)
    if (resizeHandler) window.removeEventListener('resize', resizeHandler)
    obs.disconnect()
  })
})
</script>

<template>
  <div class="bg-fx">
    <canvas ref="canvas" class="pinwheel-bg" />
    <div class="bg-grid" />
  </div>
</template>

<style scoped>
.bg-fx {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.pinwheel-bg {
  position: absolute;
  inset: 0;
  display: block;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(124, 58, 237, 0.05) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(124, 58, 237, 0.05) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 40%, black, transparent 75%);
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 40%, black, transparent 75%);
}

:global(.dark) .bg-grid {
  background-image:
    linear-gradient(to right, rgba(196, 181, 253, 0.06) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(196, 181, 253, 0.06) 1px, transparent 1px);
}

@media (prefers-reduced-motion: reduce) {
  .pinwheel-bg {
    display: none;
  }
}
</style>
