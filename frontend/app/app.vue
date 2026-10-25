<template>
  <!-- a thin wheat-to-olive bar across the top while a page loads -->
  <NuxtLoadingIndicator color="linear-gradient(90deg, #e3a43d, #6b8e23)" :height="3" />
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>
