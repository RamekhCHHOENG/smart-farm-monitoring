import type { Component } from "vue";

/** One item of an ActionMenu (the "⋯" button). */
export interface MenuAction {
  label: string;
  icon?: Component;
  danger?: boolean;
  /** a thin line above this item */
  separated?: boolean;
  run: () => unknown;
}
