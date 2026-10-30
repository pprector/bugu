<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import { NButton, NSelect, NTag } from 'naive-ui';
import { fetchAdminAiStatus, fetchAdminGenLogs, type AdminGenLog, type AdminGenLogSummary } from '@/service/api';

const SCENE_OPTIONS = ['CREATE', 'REPUSH', 'WAKEUP'].map(v => ({ label: v, value: v }));

const loading = ref(false);
const scene = ref<string | null>(null);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const logs = ref<AdminGenLog[]>([]);
const summary = ref<AdminGenLogSummary[]>([]);
const aiStatus = ref<{ llmProvider: string; gatewayRegistered: boolean; note: string } | null>(null);

const summaryColumns = [
  { title: '场景', key: 'scene', width: 120 },
  { title: '生成次数', key: 'total', width: 100 },
  { title: '降级次数', key: 'degraded', width: 100 },
  {
    title: '降级率',
    key: 'degradeRate',
    render: (row: AdminGenLogSummary) => `${(row.degradeRate * 100).toFixed(1)}%`
  }
];

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '场景', key: 'scene', width: 110 },
  {
    title: '语气',
    key: 'tone',
    width: 90,
    render: (row: AdminGenLog) => row.tone ?? '-'
  },
  {
    title: '是否降级',
    key: 'degraded',
    width: 100,
    render: (row: AdminGenLog) =>
      h(NTag, { size: 'small', type: row.degraded ? 'warning' : 'success' }, () => (row.degraded ? '降级' : '正常'))
  },
  {
    title: '耗时(ms)',
    key: 'latencyMs',
    width: 100,
    render: (row: AdminGenLog) => row.latencyMs ?? '-'
  },
  {
    title: '时间',
    key: 'createdAt',
    minWidth: 170,
    render: (row: AdminGenLog) => row.createdAt.slice(0, 19).replace('T', ' ')
  }
];

async function load() {
  loading.value = true;
  try {
    const { data, error } = await fetchAdminGenLogs({
      scene: scene.value ?? undefined,
      page: page.value,
      size: pageSize.value
    });
    if (!error && data) {
      summary.value = data.summary;
      logs.value = data.logs;
      // 服务端不返回 total:满页时假设还有下一页,不满页即尾页
      total.value =
        data.logs.length === pageSize.value ? page.value * pageSize.value : (page.value - 1) * pageSize.value + data.logs.length;
    }
  } finally {
    loading.value = false;
  }
}

async function loadStatus() {
  const { data, error } = await fetchAdminAiStatus();
  if (!error && data) aiStatus.value = data;
}

function changePage(next: number) {
  page.value = next;
  load();
}

onMounted(() => {
  load();
  loadStatus();
});
</script>

<template>
  <NSpace vertical :size="16">
    <NCard title="AI 状态" :bordered="false" class="card-wrapper">
      <NDescriptions v-if="aiStatus" :column="1" label-placement="left" bordered>
        <NDescriptionsItem label="LLM 供应商">
          <NTag size="small" :type="aiStatus.gatewayRegistered ? 'success' : 'warning'">{{ aiStatus.llmProvider }}</NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="说明">{{ aiStatus.note }}</NDescriptionsItem>
      </NDescriptions>
    </NCard>

    <NCard title="降级率汇总" :bordered="false" class="card-wrapper">
      <NDataTable :columns="summaryColumns" :data="summary" :bordered="false" size="small" />
    </NCard>

    <NCard title="生成日志" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NSpace>
          <NSelect v-model:value="scene" :options="SCENE_OPTIONS" placeholder="全部场景" clearable style="width: 140px" />
          <NButton type="primary" @click="load">查询</NButton>
        </NSpace>
      </template>
      <NDataTable :columns="columns" :data="logs" :loading="loading" :bordered="false" />
      <div class="mt-12px flex justify-end">
        <NPagination :page="page" :page-size="pageSize" :item-count="total" @update:page="changePage" />
      </div>
    </NCard>
  </NSpace>
</template>
