<template>
  <!-- 移植自 soybean-admin PageTab/ChromeTabBg：左右两个镜像的几何 path 拼出
       Chrome 页签轮廓（上圆角 + 底部外扩内凹弧），fill=currentColor 由父级配色 -->
  <svg class="size-full">
    <defs>
      <symbol id="geometry-left" viewBox="0 0 214 36">
        <path d="M17 0h197v36H0v-2c4.5 0 9-3.5 9-8V8c0-4.5 3.5-8 8-8z" />
      </symbol>
      <symbol id="geometry-right" viewBox="0 0 214 36">
        <use href="#geometry-left" />
      </symbol>
      <clipPath>
        <rect width="100%" height="100%" x="0" />
      </clipPath>
    </defs>
    <svg width="51%" height="100%">
      <use href="#geometry-left" width="214" height="36" fill="currentColor" />
    </svg>
    <g transform="scale(-1, 1)">
      <svg width="51%" height="100%" x="-100%" y="0">
        <use href="#geometry-right" width="214" height="36" fill="currentColor" />
      </svg>
    </g>
  </svg>
</template>
