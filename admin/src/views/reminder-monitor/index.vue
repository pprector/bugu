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
const page = ref(1);
const size = ref(20);
const total = ref(0);

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

let requestId = 0;

async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  try {
    const { data, error } = await fetchAdminReminders({
      status: status.value ?? undefined,
      query: keyword.value || undefined,
      page: page.value,
      size: size.value
    });
    if (currentRequest !== requestId) return;
    if (!error && data) {
      const lastPage = Math.max(1, Math.ceil(data.total / data.size));
      if (data.page > lastPage) {
        page.value = lastPage;
        await load();
        return;
      }
      rows.value = data.items;
      total.value = data.total;
      page.value = data.page;
      size.value = data.size;
    }
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}

function search() {
  page.value = 1;
  load();
}

function changePage(value: number) {
  page.value = value;
  load();
}

function changeSize(value: number) {
  size.value = value;
  page.value = 1;
  load();
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="提醒监控" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NSpace>
          <NSelect v-model:value="status" :options="STATUS_OPTIONS" placeholder="全部状态" clearable style="width: 150px" />
          <NInput v-model:value="keyword" placeholder="标题 / ID" clearable style="width: 180px" @keyup.enter="search" />
          <NButton type="primary" @click="search">查询</NButton>
        </NSpace>
      </template>
      <NDataTable
        remote
        :columns="columns"
        :data="rows"
        :row-key="row => row.id"
        :loading="loading"
        :bordered="false"
        :pagination="{
          page,
          pageSize: size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50, 100]
        }"
        @update:page="changePage"
        @update:page-size="changeSize"
      />
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
