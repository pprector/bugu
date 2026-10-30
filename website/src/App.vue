<script setup lang="ts">
import SiteHeader from './components/SiteHeader.vue'
import HeroSection from './components/HeroSection.vue'
import PillarsSection from './components/PillarsSection.vue'
import HowSection from './components/HowSection.vue'
import CompanionSection from './components/CompanionSection.vue'
import PromiseSection from './components/PromiseSection.vue'
import DownloadSection from './components/DownloadSection.vue'
import SiteFooter from './components/SiteFooter.vue'
</script>

<template>
  <SiteHeader />
  <main>
    <HeroSection />
    <PillarsSection />
    <HowSection />
    <CompanionSection />
    <PromiseSection />
    <DownloadSection />
  </main>
  <SiteFooter />
</template>
