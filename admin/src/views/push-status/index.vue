<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchAdminPushStatus, type AdminPushStatus } from '@/service/api';

const loading = ref(false);
const status = ref<AdminPushStatus | null>(null);

async function load() {
  loading.value = true;
  try {
    const { data, error } = await fetchAdminPushStatus();
    if (!error && data) status.value = data;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="推送状态" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NButton type="primary" :loading="loading" @click="load">刷新</NButton>
      </template>
      <NDescriptions v-if="status" :column="1" label-placement="left" bordered>
        <NDescriptionsItem label="已注册通道">
          <NTag size="small">{{ status.channel }}</NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="说明">{{ status.description }}</NDescriptionsItem>
      </NDescriptions>
      <NEmpty v-else description="暂无数据" />
    </NCard>
  </div>
</template>
