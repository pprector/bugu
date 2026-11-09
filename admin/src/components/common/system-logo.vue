<template>
  <div class="app-logo">
    <svg width="100%" height="100%" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <mask id="bugu-admin-logo-mask" maskUnits="userSpaceOnUse" x="0" y="0" width="108" height="108">
          <g fill="#FFFFFF">
            <path d="M46.5,30.5 A3.2,3.2 0 1,1 46.4,30.4 Z" />
            <path d="M55,27.5 A3.6,3.6 0 1,1 54.9,27.4 Z" />
            <g transform="rotate(-18 38 62)">
              <path d="M38,53 C 33.5,53 31,57.5 31,62.5 C 31,67.5 33.5,72 38,72 C 42.5,72 45,67.5 45,62.5 C 45,57.5 42.5,53 38,53 Z" />
            </g>
            <g transform="rotate(18 70 62)">
              <path d="M70,53 C 65.5,53 63,57.5 63,62.5 C 63,67.5 65.5,72 70,72 C 74.5,72 77,67.5 77,62.5 C 77,57.5 74.5,53 70,53 Z" />
            </g>
            <path d="M54,34 C 66.5,34 75,44 75,55.5 C 75,67 66.5,76 54,76 C 41.5,76 33,67 33,55.5 C 33,44 41.5,34 54,34 Z" />
          </g>
          <g fill="#000000">
            <path d="M46.4,48.5 A2.1,2.1 0 1,1 46.3,48.4 Z" />
            <path d="M61.6,48.5 A2.1,2.1 0 1,1 61.5,48.4 Z" />
            <path d="M50,53 L58,53 L54,58.5 Z" />
            <path d="M52,59.5 L56,59.5 L54,63.5 Z" />
          </g>
        </mask>
      </defs>
      <g transform="translate(-26.43,-19.15)">
        <rect width="108" height="108" fill="currentColor" mask="url(#bugu-admin-logo-mask)" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.app-logo {
  color: rgb(var(--primary-600-color));
}
</style>
