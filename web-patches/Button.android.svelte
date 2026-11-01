<script lang="ts">
  import { tv, type VariantProps } from "tailwind-variants";
  import type { HTMLButtonAttributes } from "svelte/elements";
  import type { Snippet } from "svelte";

  const button = tv({
    base: "inline-flex items-center justify-center gap-2 font-medium transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed",
    variants: {
      variant: {
        primary: "bg-[var(--color-accent)] text-[var(--color-on-accent)] hover:bg-[var(--color-accent-hover)]",
        secondary: "bg-[var(--color-surface-2)] text-[var(--color-ink-primary)] border border-[var(--color-border)] hover:bg-[var(--color-surface-3)] hover:border-[var(--color-border-emphasis)]",
        ghost: "bg-transparent text-[var(--color-ink-secondary)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-ink-primary)]",
        danger: "bg-[var(--color-status-danger)] text-[var(--color-surface-0)]",
      },
      size: {
        sm: "min-h-12 px-4 text-[14px] rounded-full",
        md: "min-h-12 px-5 text-[14px] rounded-full",
        lg: "min-h-14 px-6 text-[14px] rounded-full",
        icon: "h-12 w-12 shrink-0 rounded-full",
      },
    },
    defaultVariants: {
      variant: "secondary",
      size: "md",
    },
  });

  type ButtonVariants = VariantProps<typeof button>;

  interface Props extends HTMLButtonAttributes {
    variant?: ButtonVariants["variant"];
    size?: ButtonVariants["size"];
    children: Snippet;
    class?: string;
  }

  let { variant, size, children, class: className = "", ...rest }: Props = $props();
</script>

<button class="{button({ variant, size })} {className}" {...rest}>
  {@render children()}
</button>
