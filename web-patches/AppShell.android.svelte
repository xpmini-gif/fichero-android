<script lang="ts">
  import Toolbar from "./Toolbar.svelte";
  import StatusBar from "./StatusBar.svelte";
  import PanelShell from "./PanelShell.svelte";
  import Toast from "$/components/ui/Toast.svelte";
</script>

<div class="flex h-[100dvh] min-h-0 flex-col overflow-hidden bg-[var(--color-surface-0)]">
  <Toolbar />
  <PanelShell />
  <StatusBar />
</div>

<Toast />
