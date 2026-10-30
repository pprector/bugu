<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue';
import { NButton, NTag, type DataTableColumns } from 'naive-ui';
import {
  fetchAdminFeedback,
  fetchAdminUpdateFeedback,
  type AdminFeedback,
  type AdminFeedbackStatus
} from '@/service/api';
import { useAuth } from '@/hooks/business/auth';
import { $t } from '@/locales';

const { hasAuth } = useAuth();
const canWrite = computed(() => hasAuth('admin:write'));
const loading = ref(false);
const saving = ref(false);
const rows = ref<AdminFeedback[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);
const filterStatus = ref<AdminFeedbackStatus | null>(null);
const selected = ref<AdminFeedback | null>(null);
const modalVisible = ref(false);
const form = ref<{ status: AdminFeedbackStatus; reply: string }>({ status: 'OPEN', reply: '' });
const statuses: AdminFeedbackStatus[] = ['OPEN', 'PROCESSING', 'RESOLVED'];
const statusOptions = computed(() => statuses.map(value => ({ label: $t(`page.feedback.statuses.${value}`), value })));
const statusTypes = { OPEN: 'warning', PROCESSING: 'info', RESOLVED: 'success' } as const;

const columns = computed<DataTableColumns<AdminFeedback>>(() => [
  { title: 'ID', key: 'id', width: 80 },
  { title: $t('page.feedback.userId'), key: 'userId', width: 100 },
  { title: $t('page.feedback.category'), key: 'category', width: 140 },
  { title: $t('page.feedback.content'), key: 'content', minWidth: 240, ellipsis: { tooltip: true } },
  {
    title: $t('page.feedback.appVersion'), key: 'appVersion', width: 120,
    render: row => row.appVersion || '-'
  },
  {
    title: $t('page.feedback.status'), key: 'status', width: 120,
    render: row => h(NTag, { size: 'small', type: statusTypes[row.status] }, () => $t(`page.feedback.statuses.${row.status}`))
  },
  { title: $t('page.feedback.createdAt'), key: 'createdAt', width: 190 },
  {
    title: $t('common.operate'), key: 'actions', width: 100, fixed: 'right',
    render: row => h(NButton, { size: 'small', onClick: () => openDetail(row) }, () => $t('page.feedback.view'))
  }
]);

let requestId = 0;
async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  try {
    const { data, error } = await fetchAdminFeedback({
      page: page.value,
      size: size.value,
      status: filterStatus.value ?? undefined
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

function changeStatus(value: AdminFeedbackStatus | null) {
  filterStatus.value = value;
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

function openDetail(row: AdminFeedback) {
  selected.value = row;
  form.value = { status: row.status, reply: row.reply || '' };
  modalVisible.value = true;
}

async function save() {
  if (!canWrite.value || !selected.value || saving.value) return;
  const reply = form.value.reply.trim();
  if (form.value.status === 'RESOLVED' && !reply) {
    window.$message?.warning($t('page.feedback.replyRequired'));
    return;
  }
  saving.value = true;
  try {
    const { data, error } = await fetchAdminUpdateFeedback(selected.value.id, {
      status: form.value.status,
      reply: reply || null
    });
    if (!error && data) {
      selected.value = data;
      window.$message?.success($t('page.feedback.saved'));
      modalVisible.value = false;
      await load();
    }
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard :title="$t('route.feedback-manage')" :bordered="false" class="card-wrapper">
      <NSpace class="mb-16px" align="center">
        <NSelect
          :value="filterStatus"
          :options="statusOptions"
          :placeholder="$t('page.feedback.allStatuses')"
          :aria-label="$t('page.feedback.status')"
          clearable
          class="w-180px"
          @update:value="changeStatus"
        />
        <NButton :loading="loading" @click="load">{{ $t('common.refresh') }}</NButton>
      </NSpace>
      <NDataTable
        remote
        :columns="columns"
        :data="rows"
        :row-key="row => row.id"
        :loading="loading"
        :bordered="false"
        :scroll-x="1190"
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
    <NModal
      v-model:show="modalVisible"
      preset="card"
      :title="$t('page.feedback.detail')"
      class="w-680px max-w-95vw"
      :mask-closable="!saving"
      :close-on-esc="!saving"
      :closable="!saving"
    >
      <template v-if="selected">
        <NDescriptions :column="1" label-placement="left" bordered class="mb-16px">
          <NDescriptionsItem label="ID">{{ selected.id }}</NDescriptionsItem>
          <NDescriptionsItem :label="$t('page.feedback.userId')">{{ selected.userId }}</NDescriptionsItem>
          <NDescriptionsItem :label="$t('page.feedback.category')">{{ selected.category }}</NDescriptionsItem>
          <NDescriptionsItem :label="$t('page.feedback.appVersion')">{{ selected.appVersion || '-' }}</NDescriptionsItem>
          <NDescriptionsItem :label="$t('page.feedback.createdAt')">{{ selected.createdAt }}</NDescriptionsItem>
          <NDescriptionsItem :label="$t('page.feedback.updatedAt')">{{ selected.updatedAt }}</NDescriptionsItem>
          <NDescriptionsItem :label="$t('page.feedback.content')">
            <div class="whitespace-pre-wrap break-all">{{ selected.content }}</div>
          </NDescriptionsItem>
        </NDescriptions>
        <NForm label-placement="top" :disabled="!canWrite || saving">
          <NFormItem :label="$t('page.feedback.status')">
            <NSelect v-model:value="form.status" :options="statusOptions" />
          </NFormItem>
          <NFormItem :label="$t('page.feedback.reply')" :required="form.status === 'RESOLVED'">
            <NInput
              v-model:value="form.reply"
              type="textarea"
              :maxlength="2000"
              :autosize="{ minRows: 4, maxRows: 12 }"
              :placeholder="$t('page.feedback.replyPlaceholder')"
              show-count
            />
          </NFormItem>
        </NForm>
        <NSpace justify="end">
          <NButton :disabled="saving" @click="modalVisible = false">{{ $t('common.close') }}</NButton>
          <NButton v-if="canWrite" type="primary" :loading="saving" @click="save">{{ $t('common.confirm') }}</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
