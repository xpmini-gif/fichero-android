<script lang="ts">
  import { Pane, PaneGroup, PaneResizer } from "paneforge";
  import { Sidebar, SlidersHorizontal, SquaresFour } from "phosphor-svelte";
  import Icon from "$/components/ui/Icon.svelte";
  import CanvasPane from "$/components/canvas/CanvasPane.svelte";
  import RightPanel from "$/components/right-panel/RightPanel.svelte";
  import LeftPanel from "$/components/left-panel/LeftPanel.svelte";

  const STORAGE_KEY = "fichero:panel-layout";

  let windowWidth = $state(window.innerWidth);
  let leftCollapsed = $state(false);
  let rightCollapsed = $state(false);
  let mobilePanel = $state<"canvas" | "objects" | "properties">("canvas");
  let isMobile = $derived(windowWidth < 700);

  function toggleLeft() {
    leftCollapsed = !leftCollapsed;
    saveState();
  }

  function toggleRight() {
    rightCollapsed = !rightCollapsed;
    saveState();
  }

  function saveState() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ leftCollapsed, rightCollapsed }));
    } catch {}
  }

  function loadState() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const s = JSON.parse(raw);
        if (typeof s.leftCollapsed === "boolean") leftCollapsed = s.leftCollapsed;
        if (typeof s.rightCollapsed === "boolean") rightCollapsed = s.rightCollapsed;
      }
    } catch {}
  }

  loadState();
</script>

<svelte:window bind:innerWidth={windowWidth} />

{#if isMobile}
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="min-h-0 flex-1">
      <CanvasPane />
    </div>

    {#if mobilePanel !== "canvas"}
      <section class="h-[38dvh] min-h-[210px] max-h-[360px] shrink-0 overflow-hidden border-t border-[var(--color-border)] bg-[var(--color-surface-1)]">
        <div class="flex h-8 items-center justify-between border-b border-[var(--color-border-soft)] px-3">
          <span class="text-[11px] font-medium uppercase tracking-wider text-[var(--color-ink-secondary)]">
            {mobilePanel === "objects" ? "Objects" : "Properties"}
          </span>
          <button
            class="rounded px-2 py-1 text-[11px] text-[var(--color-ink-secondary)]"
            onclick={() => mobilePanel = "canvas"}
          >
            Close
          </button>
        </div>
        <div class="h-[calc(100%-32px)] overflow-y-auto overscroll-contain p-2">
          {#if mobilePanel === "objects"}
            <LeftPanel />
          {:else}
            <RightPanel />
          {/if}
        </div>
      </section>
    {/if}

    <nav class="grid h-12 shrink-0 grid-cols-3 border-t border-[var(--color-border)] bg-[var(--color-surface-1)]">
      <button
        class="flex items-center justify-center gap-1.5 text-[11px] {mobilePanel === 'objects' ? 'text-[var(--color-accent)] bg-[var(--color-accent-subtle)]' : 'text-[var(--color-ink-secondary)]'}"
        onclick={() => mobilePanel = mobilePanel === "objects" ? "canvas" : "objects"}
      >
        <Icon icon={Sidebar} size="md" />
        Objects
      </button>
      <button
        class="flex items-center justify-center gap-1.5 border-x border-[var(--color-border)] text-[11px] {mobilePanel === 'canvas' ? 'text-[var(--color-accent)] bg-[var(--color-accent-subtle)]' : 'text-[var(--color-ink-secondary)]'}"
        onclick={() => mobilePanel = "canvas"}
      >
        <Icon icon={SquaresFour} size="md" />
        Canvas
      </button>
      <button
        class="flex items-center justify-center gap-1.5 text-[11px] {mobilePanel === 'properties' ? 'text-[var(--color-accent)] bg-[var(--color-accent-subtle)]' : 'text-[var(--color-ink-secondary)]'}"
        onclick={() => mobilePanel = mobilePanel === "properties" ? "canvas" : "properties"}
      >
        <Icon icon={SlidersHorizontal} size="md" />
        Properties
      </button>
    </nav>
  </div>
{:else}
  <PaneGroup direction="horizontal" class="flex-1 overflow-hidden">
    {#if leftCollapsed}
      <div class="flex w-[var(--panel-collapsed-w)] shrink-0 flex-col items-center border-r border-[var(--color-border)] bg-[var(--color-surface-1)] py-2">
        <button
          class="flex h-7 w-7 items-center justify-center rounded-[var(--radius-sm)] text-[var(--color-ink-tertiary)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-ink-secondary)]"
          onclick={toggleLeft}
          title="Expand panel"
        >
          <Icon icon={Sidebar} size="md" />
        </button>
      </div>
    {:else}
      <Pane defaultSize={20} minSize={15} maxSize={30}>
        <div class="flex h-full flex-col border-r border-[var(--color-border)] bg-[var(--color-surface-1)]">
          <div class="flex items-center justify-between border-b border-[var(--color-border-soft)] px-2 py-1.5">
            <span class="text-[11px] font-medium uppercase tracking-wider text-[var(--color-ink-secondary)]">Objects</span>
            <button
              class="flex h-5 w-5 items-center justify-center rounded-[var(--radius-sm)] text-[var(--color-ink-tertiary)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-ink-secondary)]"
              onclick={toggleLeft}
              title="Collapse panel"
            >
              <Icon icon={Sidebar} size="sm" />
            </button>
          </div>
          <div class="flex-1 overflow-y-auto">
            <LeftPanel />
          </div>
        </div>
      </Pane>
      <PaneResizer class="w-1 cursor-col-resize bg-transparent transition-colors hover:bg-[var(--color-accent-subtle)] active:bg-[var(--color-accent-subtle)]" />
    {/if}

    <Pane defaultSize={60}>
      <CanvasPane />
    </Pane>

    {#if rightCollapsed}
      <div class="flex w-[var(--panel-collapsed-w)] shrink-0 flex-col items-center border-l border-[var(--color-border)] bg-[var(--color-surface-1)] py-2">
        <button
          class="flex h-7 w-7 items-center justify-center rounded-[var(--radius-sm)] text-[var(--color-ink-tertiary)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-ink-secondary)]"
          onclick={toggleRight}
          title="Expand panel"
        >
          <Icon icon={SlidersHorizontal} size="md" />
        </button>
      </div>
    {:else}
      <PaneResizer class="w-1 cursor-col-resize bg-transparent transition-colors hover:bg-[var(--color-accent-subtle)] active:bg-[var(--color-accent-subtle)]" />
      <Pane defaultSize={20} minSize={18} maxSize={33}>
        <div class="flex h-full flex-col border-l border-[var(--color-border)] bg-[var(--color-surface-1)]">
          <div class="flex items-center justify-between border-b border-[var(--color-border-soft)] px-2 py-1.5">
            <span class="text-[11px] font-medium uppercase tracking-wider text-[var(--color-ink-secondary)]">Properties</span>
            <button
              class="flex h-5 w-5 items-center justify-center rounded-[var(--radius-sm)] text-[var(--color-ink-tertiary)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-ink-secondary)]"
              onclick={toggleRight}
              title="Collapse panel"
            >
              <Icon icon={SlidersHorizontal} size="sm" />
            </button>
          </div>
          <div class="flex-1 overflow-y-auto p-2">
            <RightPanel />
          </div>
        </div>
      </Pane>
    {/if}
  </PaneGroup>
{/if}
