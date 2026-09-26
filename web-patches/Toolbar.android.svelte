<script lang="ts">
  import Button from "$/components/ui/Button.svelte";
  import Icon from "$/components/ui/Icon.svelte";
  import Tooltip from "$/components/ui/Tooltip.svelte";
  import {
    ArrowCounterClockwise,
    ArrowClockwise,
    MagnifyingGlassMinus,
    MagnifyingGlassPlus,
    GridFour,
    Eye,
    Printer,
  } from "phosphor-svelte";
  import { canvasStore } from "$/stores/canvas-store.svelte";
  import { connectionState } from "$/stores";
  import PrinterStatus from "$/components/print/PrinterStatus.svelte";
  import PrintPreview from "$/components/print/PrintPreview.svelte";

  let previewOpen = $state(false);
  let printNow = $state(false);
  let windowWidth = $state(window.innerWidth);
  let isMobile = $derived(windowWidth < 700);

  function openPreview() {
    printNow = false;
    previewOpen = true;
  }

  function openPrintNow() {
    printNow = true;
    previewOpen = true;
  }
</script>

<svelte:window bind:innerWidth={windowWidth} />

{#if isMobile}
  <header class="flex h-12 shrink-0 items-center gap-1 border-b border-[var(--color-border)] bg-[var(--color-surface-1)] px-2">
    <img src="{import.meta.env.BASE_URL}logo.png" alt="Fichero" class="h-7 w-7 rounded" />
    <div class="flex-1"></div>

    <Tooltip text="Preview">
      <Button variant="secondary" size="icon" onclick={openPreview}>
        <Icon icon={Eye} size="md" />
      </Button>
    </Tooltip>
    <Tooltip text="Print">
      <Button variant="primary" size="icon" disabled={$connectionState !== "connected"} onclick={openPrintNow}>
        <Icon icon={Printer} size="md" />
      </Button>
    </Tooltip>
    <PrinterStatus compact />
  </header>
{:else}
  <header class="flex h-[var(--toolbar-h)] shrink-0 items-center border-b border-[var(--color-border)] bg-[var(--color-surface-1)] px-3">
    <div class="flex items-center gap-2">
      <img src="{import.meta.env.BASE_URL}logo.png" alt="Fichero" class="h-6 rounded" />
    </div>

    <div class="flex flex-1 items-center justify-center gap-1">
      <Tooltip text="Undo (Ctrl+Z)">
        <Button variant="ghost" size="icon" disabled>
          <Icon icon={ArrowCounterClockwise} size="lg" />
        </Button>
      </Tooltip>
      <Tooltip text="Redo (Ctrl+Y)">
        <Button variant="ghost" size="icon" disabled>
          <Icon icon={ArrowClockwise} size="lg" />
        </Button>
      </Tooltip>

      <div class="mx-1 h-4 w-px bg-[var(--color-border)]"></div>

      <Tooltip text="Zoom out">
        <Button variant="ghost" size="icon" onclick={() => canvasStore.zoomOut?.()}>
          <Icon icon={MagnifyingGlassMinus} size="lg" />
        </Button>
      </Tooltip>
      <button
        class="w-10 text-center text-[11px] text-[var(--color-ink-secondary)] hover:text-[var(--color-ink-primary)]"
        onclick={() => canvasStore.zoomFit?.()}
        title="Fit to view"
      >{canvasStore.zoomPercent}%</button>
      <Tooltip text="Zoom in">
        <Button variant="ghost" size="icon" onclick={() => canvasStore.zoomIn?.()}>
          <Icon icon={MagnifyingGlassPlus} size="lg" />
        </Button>
      </Tooltip>

      <div class="mx-1 h-4 w-px bg-[var(--color-border)]"></div>

      <Tooltip text="Toggle grid">
        <Button variant="ghost" size="icon">
          <Icon icon={GridFour} size="lg" />
        </Button>
      </Tooltip>
    </div>

    <div class="flex items-center gap-1">
      <Button variant="secondary" size="md" onclick={openPreview}>
        <Icon icon={Eye} size="md" />
        Preview
      </Button>
      <Button variant="primary" size="md" disabled={$connectionState !== "connected"} onclick={openPrintNow}>
        <Icon icon={Printer} size="md" />
        Print
      </Button>

      <div class="mx-1 h-4 w-px bg-[var(--color-border)]"></div>

      <PrinterStatus />
    </div>
  </header>
{/if}

{#if previewOpen}
  <PrintPreview bind:show={previewOpen} {printNow} />
{/if}
