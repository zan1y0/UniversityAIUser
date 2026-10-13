<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useAppStore } from '../stores/app';
import { useStudyStore } from '../stores/study';
import { localDate } from '../domain/logic';
import AvatarGuide from '../components/AvatarGuide.vue';
import ChatPanel from '../components/ChatPanel.vue';
import AppIcon from '../components/AppIcon.vue';
import LiveUnavailable from '../components/LiveUnavailable.vue';
const app = useAppStore();
const study = useStudyStore();
const mode = ref('温柔');
const seconds = ref(25 * 60);
const running = ref(false);
const focusSubject = ref<'数学' | '英语' | '政治' | '专业课'>('数学');
let end = 0;
let timer: ReturnType<typeof setInterval> | undefined;
const display = computed(
  () =>
    `${String(Math.floor(seconds.value / 60)).padStart(2, '0')}:${String(seconds.value % 60).padStart(2, '0')}`,
);
function stop() {
  running.value = false;
  if (timer) clearInterval(timer);
}
function toggle() {
  if (running.value) {
    stop();
    return;
  }
  if (seconds.value === 0) seconds.value = 25 * 60;
  end = Date.now() + seconds.value * 1000;
  running.value = true;
  timer = setInterval(() => {
    seconds.value = Math.max(0, Math.ceil((end - Date.now()) / 1000));
    if (!seconds.value) {
      stop();
      study.state.events.push({
        subject: focusSubject.value,
        minutes: 25,
        date: localDate(),
        focused: true,
      });
      app.notify('本轮专注已完成，休息一下吧');
    }
  }, 250);
}
function reset() {
  stop();
  seconds.value = 25 * 60;
}
onBeforeUnmount(stop);
</script>
<template>
  <div class="split">
    <div class="stack">
      <ChatPanel
        purpose="陪伴督学"
        greeting="你好，我是研小途。今天想先完成什么小目标？"
        :context="`陪伴模式：${mode}；提供学习提醒、鼓励与情绪安抚。`"
        :prompts="['帮我拆解今天的学习目标', '今天有点累，想聊一聊', '提醒我开始一轮专注学习']"
      />
      <section class="card">
        <h2>学习提醒</h2>
        <p class="muted small space-top">
          {{
            study.state.plan
              ? `当前计划有 ${study.state.plan.tasks.filter((t) => !t.done && t.date <= localDate()).length} 项到期或待完成任务。`
              : '确认学习计划后，可以在这里查看待完成事项。'
          }}
        </p>
        <label class="reminder-row"
          >开启演示页内提醒<input
            v-model="study.state.reminders"
            type="checkbox"
            :disabled="app.mode === 'live'"
        /></label>
        <p class="muted small space-top">
          此偏好只控制演示页面提醒展示；浏览器关闭后的提醒需后端调度与通知接口。
        </p>
        <div
          v-if="
            study.state.reminders &&
            app.mode === 'demo' &&
            study.state.plan?.tasks.some((t) => !t.done && t.date < localDate())
          "
          class="notice space-top"
        >
          有超期任务。可以前往学习计划调整安排，给自己一个可实现的节奏。
        </div>
      </section>
    </div>
    <div class="stack">
      <section class="card">
        <div class="card-heading">
          <h2>研小途</h2>
          <span class="tag green">陪你一起学</span>
        </div>
        <AvatarGuide :mood="mode" />
        <div class="chips space-top">
          <button
            v-for="m in ['学霸', '温柔', '严管']"
            :key="m"
            class="chip"
            :class="{ active: mode === m }"
            @click="mode = m"
          >
            {{ m }}模式
          </button>
        </div>
        <p class="muted small space-top">
          {{
            mode === '学霸'
              ? '重视解题思路和复盘，学得明白。'
              : mode === '严管'
                ? '明确当前目标，按计划完成与检查。'
                : '照顾学习节奏，鼓励与休息都很重要。'
          }}
        </p>
      </section>
      <section class="card">
        <div class="card-heading">
          <h2>今日打卡</h2>
          <span class="muted small">累计 {{ study.state.checkins.length }} 天</span>
        </div>
        <LiveUnavailable v-if="app.mode === 'live'" capability="打卡与专注记录" /><template v-else
          ><div class="checkin-strip">
            <span
              v-for="i in 7"
              :key="i"
              class="checkin-day"
              :class="{
                active: study.state.checkins.includes(
                  localDate(new Date(Date.now() - (7 - i) * 86400000)),
                ),
              }"
              >{{ new Date(Date.now() - (7 - i) * 86400000).getDate() }}</span
            >
          </div>
          <button
            class="btn stretch"
            :disabled="study.checkedIn"
            @click="
              study.checkin();
              app.notify('今日打卡已记录');
            "
          >
            <AppIcon name="check" :size="16" />{{
              study.checkedIn ? '今日已打卡' : '记录今天的坚持'
            }}
          </button></template
        >
      </section>
      <section class="card">
        <h2>专注一小步</h2>
        <template v-if="app.mode === 'demo'"
          ><label class="field space-top"
            >学习科目<select v-model="focusSubject" :disabled="running">
              <option v-for="s in ['数学', '英语', '政治', '专业课']" :key="s">{{ s }}</option>
            </select></label
          >
          <div class="timer" role="timer">{{ display }}</div>
          <div class="row">
            <button class="btn stretch" @click="toggle">
              <AppIcon :name="running ? 'pause' : 'play'" :size="16" />{{
                running ? '暂停' : '开始专注'
              }}</button
            ><button class="icon-btn" aria-label="重置专注计时" @click="reset">
              <AppIcon name="reset" />
            </button>
          </div>
          <p class="muted small space-top">
            完成 25 分钟才计入专注记录。离开页面后计时停止；计时不代表真实注意力检测。
          </p></template
        ><LiveUnavailable v-else capability="专注事件记录" />
      </section>
    </div>
  </div>
</template>
