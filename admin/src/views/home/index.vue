<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchAdminStats, type AdminStats } from '@/service/api';
import { useRouterPush } from '@/hooks/common/router';

const { routerPushByKey } = useRouterPush();

const stats = ref<AdminStats | null>(null);

const statItems = [
  { key: 'userTotal', label: '用户总数', icon: 'mdi:account-outline' },
  { key: 'userElder', label: '长辈用户', icon: 'mdi:account-heart-outline' },
  { key: 'bondActive', label: '活跃绑定', icon: 'mdi:link-variant' },
  { key: 'reminderPending', label: '待触发提醒', icon: 'mdi:alarm-multiple' },
  { key: 'fireToday', label: '今日触发', icon: 'mdi:bell-ring-outline' },
  { key: 'aiGenToday', label: '今日 AI 生成', icon: 'mdi:text-box-multiple-outline' }
] as const;

type StatKey = (typeof statItems)[number]['key'];

const statValues = computed<Record<StatKey, string>>(() => {
  const values = {} as Record<StatKey, string>;
  for (const item of statItems) {
    values[item.key] = stats.value ? String(stats.value[item.key]) : '--';
  }
  return values;
});

const entries = [
  { routeKey: 'user-manage', icon: 'mdi:account-group-outline', desc: '查询用户、标记长辈模式' },
  { routeKey: 'admin-account', icon: 'mdi:shield-account-outline', desc: '后台账号与角色启停' },
  { routeKey: 'bond-manage', icon: 'mdi:link-variant', desc: '查看绑定关系、管理员解绑' },
  { routeKey: 'reminder-monitor', icon: 'mdi:alarm-multiple', desc: '提醒状态与升级链日志' },
  { routeKey: 'push-status', icon: 'mdi:bell-ring-outline', desc: '推送通道注册状态(只读)' },
  { routeKey: 'ai-template', icon: 'mdi:file-document-edit-outline', desc: '降级文案模板增改启停' },
  { routeKey: 'ai-log', icon: 'mdi:text-box-multiple-outline', desc: '生成日志与降级率观测' },
  { routeKey: 'audit-log', icon: 'mdi:clipboard-text-outline', desc: '后台写操作审计' }
] as const;

async function loadStats() {
  // 加载失败时静默保持 '--',由 request 封装统一提示错误
  const { data, error } = await fetchAdminStats();
  if (!error && data) stats.value = data;
}

onMounted(loadStats);
</script>

<template>
  <NSpace vertical :size="16">
    <NCard :bordered="false" class="card-wrapper">
      <div class="py-24px text-center">
        <div class="text-22px font-bold">布谷 · 运营概览</div>
        <div class="mt-8px text-14px text-gray-500">
          谁绑了谁、哪条提醒卡在升级链、AI 降级了多少次——不必直连数据库
        </div>
      </div>
    </NCard>
    <NGrid :x-gap="16" :y-gap="16" responsive="screen" item-responsive cols="2 s:3 m:3 l:6">
      <NGi v-for="item in statItems" :key="item.key">
        <NCard size="small" :bordered="false" class="card-wrapper">
          <div class="flex items-center gap-12px">
            <div class="text-28px text-primary">
              <SvgIcon :icon="item.icon" />
            </div>
            <div>
              <div class="text-22px font-bold">{{ statValues[item.key] }}</div>
              <div class="mt-4px text-12px text-gray-500">{{ item.label }}</div>
            </div>
          </div>
        </NCard>
      </NGi>
    </NGrid>
    <NGrid :x-gap="16" :y-gap="16" responsive="screen" item-responsive cols="1 s:2 m:3 l:4">
      <NGi v-for="entry in entries" :key="entry.routeKey">
        <NCard
          size="small"
          hoverable
          class="cursor-pointer card-wrapper"
          @click="routerPushByKey(entry.routeKey)"
        >
          <div class="flex items-center gap-12px">
            <div class="text-28px text-primary">
              <SvgIcon :icon="entry.icon" />
            </div>
            <div>
              <div class="font-medium">{{ $t(`route.${entry.routeKey}`) }}</div>
              <div class="mt-4px text-12px text-gray-500">{{ entry.desc }}</div>
            </div>
          </div>
        </NCard>
      </NGi>
    </NGrid>
  </NSpace>
</template>
