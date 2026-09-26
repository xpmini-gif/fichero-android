<script lang="ts">
  import { onMount } from "svelte";
  import { Utils, type AvailableTransports } from "$/lib/fichero";
  import {
    printerClient,
    connectedPrinterName,
    connectionState,
    initClient,
    printerMeta,
  } from "$/stores";
  import { Toasts } from "$/utils/toasts";
  import Button from "$/components/ui/Button.svelte";
  import Icon from "$/components/ui/Icon.svelte";
  import Tooltip from "$/components/ui/Tooltip.svelte";
  import {
    Bluetooth,
    Circle,
    Power,
    SpinnerGap,
  } from "phosphor-svelte";

  let { compact = false } = $props<{ compact?: boolean }>();
  let featureSupport = $state<AvailableTransports>({ webBluetooth: false });

  const onConnectClicked = async () => {
    initClient();
    connectionState.set("connecting");

    try {
      await $printerClient.connect();
    } catch (e) {
      connectionState.set("disconnected");
      Toasts.error(e);
    }
  };

  const onDisconnectClicked = () => {
    $printerClient.disconnect();
  };

  onMount(() => {
    featureSupport = Utils.getAvailableTransports();
  });
</script>

{#if $connectionState === "connected"}
  <div class="flex items-center gap-1">
    {#if compact}
      <Tooltip text={$printerMeta?.model ?? $connectedPrinterName ?? "Connected"}>
        <div class="flex h-8 w-8 items-center justify-center text-[var(--color-status-success)]">
          <Circle size={9} weight="fill" />
        </div>
      </Tooltip>
    {:else}
      <Tooltip text={$printerMeta?.model ?? $connectedPrinterName ?? "Connected"}>
        <div class="flex items-center gap-1.5 px-1.5 text-[11px] text-[var(--color-status-success)]">
          <Circle size={8} weight="fill" />
          <span class="max-w-[120px] truncate text-[var(--color-ink-primary)]">
            {$printerMeta?.model ?? $connectedPrinterName}
          </span>
        </div>
      </Tooltip>
    {/if}
    <Tooltip text="Disconnect">
      <Button variant="danger" size="icon" onclick={onDisconnectClicked}>
        <Icon icon={Power} size="sm" />
      </Button>
    </Tooltip>
  </div>
{:else if $connectionState === "connecting"}
  <div class="flex h-9 items-center gap-1 px-1.5 text-[11px] text-[var(--color-ink-tertiary)]">
    <SpinnerGap size={14} class="animate-spin" />
    {#if !compact}<span>Connecting...</span>{/if}
  </div>
{:else}
  <Tooltip text={featureSupport.webBluetooth ? "Connect printer" : "Bluetooth unavailable"}>
    <Button
      variant="secondary"
      size={compact ? "icon" : "md"}
      disabled={!featureSupport.webBluetooth}
      onclick={onConnectClicked}
    >
      <Icon icon={Bluetooth} size="md" />
      {#if !compact}Connect{/if}
    </Button>
  </Tooltip>
{/if}
