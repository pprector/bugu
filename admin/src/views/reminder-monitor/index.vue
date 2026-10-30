<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import { NButton, NSelect, NTag } from 'naive-ui';
import { fetchAdminEscalations, fetchAdminReminders, type AdminEscalation, type AdminReminder } from '@/service/api';

const STATUS_OPTIONS = ['PENDING', 'FIRED', 'ACKED', 'SNOOZED', 'DECLINED', 'MISSED', 'CANCELED'].map(v => ({
  label: v,
  value: v
}));

const KIND_TAG: Record<string, 'success' | 'info' | 'warning'> = {
  ONCE: 'info',
  REPEAT: 'success',
  MUTUAL_ALARM: 'warning'
};

const loading = ref(false);
const keyword = ref('');
const status = ref<string | null>(null);
const rows = ref<AdminReminder[]>([]);

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '标题', key: 'title', minWidth: 140 },
  {
    title: '类型',
    key: 'kind',
    width: 120,
    render: (row: AdminReminder) => h(NTag, { size: 'small', type: KIND_TAG[row.kind] ?? 'default' }, () => row.kind)
  },
  { title: '状态', key: 'status', width: 100 },
  {
    title: '触发时间',
    key: 'fireAt',
    minWidth: 170,
    render: (row: AdminReminder) => row.fireAt.slice(0, 19).replace('T', ' ')
  },
  { title: '发起方', key: 'creatorName', width: 110 },
  { title: '接收方', key: 'receiverName', width: 110 },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row: AdminReminder) =>
      h(NButton, { size: 'small', onClick: () => openEscalations(row) }, () => '升级链')
  }
];

const drawerVisible = ref(false);
const drawerLoading = ref(false);
const current = ref<AdminReminder | null>(null);
const escalations = ref<AdminEscalation[]>([]);

async function openEscalations(row: AdminReminder) {
  current.value = row;
  drawerVisible.value = true;
  drawerLoading.value = true;
  try {
    const { data, error } = await fetchAdminEscalations(row.id);
    if (!error && data) escalations.value = data;
  } finally {
    drawerLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  try {
    const { data, error } = await fetchAdminReminders({
      status: status.value ?? undefined,
      query: keyword.value || undefined
    });
    if (!error && data) rows.value = data;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="提醒监控" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NSpace>
          <NSelect v-model:value="status" :options="STATUS_OPTIONS" placeholder="全部状态" clearable style="width: 150px" />
          <NInput v-model:value="keyword" placeholder="标题 / ID" clearable style="width: 180px" @keyup.enter="load" />
          <NButton type="primary" @click="load">查询</NButton>
        </NSpace>
      </template>
      <NDataTable :columns="columns" :data="rows" :loading="loading" :bordered="false" />
    </NCard>

    <NDrawer v-model:show="drawerVisible" :width="520">
      <NDrawerContent :title="`升级链 · ${current?.title ?? ''} (#${current?.id ?? ''})`" closable>
        <NSpin :show="drawerLoading">
          <NEmpty v-if="!drawerLoading && escalations.length === 0" description="该提醒暂无触发记录" />
          <NCard v-for="fire in escalations" :key="fire.fireId" size="small" class="mb-12px">
            <template #header>
              <NSpace align="center">
                <NTag size="small" :type="fire.state === 'DONE' ? 'success' : fire.state === 'MISSED' ? 'error' : 'info'">
                  {{ fire.state }}
                </NTag>
                <span class="text-12px text-gray-500">触发于 {{ fire.fireAt.slice(0, 19).replace('T', ' ') }}</span>
              </NSpace>
            </template>
            <NEmpty v-if="fire.actions.length === 0" size="small" description="无动作记录" />
            <NSteps v-else vertical size="small" :current="fire.actions.length">
              <NStep v-for="(action, index) in fire.actions" :key="index" :title="`${action.stage} · ${action.action}`">
                <span class="text-12px text-gray-500">{{ action.createdAt.slice(0, 19).replace('T', ' ') }}</span>
              </NStep>
            </NSteps>
          </NCard>
        </NSpin>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>
