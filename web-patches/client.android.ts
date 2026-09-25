import {
  SERVICE_UUID,
  WRITE_CHAR_UUID,
  NOTIFY_CHAR_UUID,
  CHUNK_SIZE,
  CHUNK_DELAY_MS,
  CMD,
} from "./constants";
import { TypedEventEmitter } from "./emitter";
import { Utils } from "./utils";
import { FicheroPrintTask } from "./print_task";
import type {
  ConnectionInfo,
  HeartbeatData,
  PrinterInfo,
  PrinterModelMeta,
  PrintProgressEvent,
  FirmwareProgressEvent,
  RfidInfo,
  Packet,
  LabelType,
  PrintTaskName,
} from "./types";

interface ClientEventMap {
  connect: { info: ConnectionInfo };
  disconnect: void;
  printerinfofetched: { info: PrinterInfo };
  heartbeat: { data: HeartbeatData };
  heartbeatfailed: { failedAttempts: number };
  printprogress: PrintProgressEvent;
  firmwareprogress: FirmwareProgressEvent;
  packetsent: { packet: Packet };
  packetreceived: { packet: Packet };
}

type NativeBridge = {
  connect(requestId: string): void;
  disconnect(): void;
  send(requestId: string, base64: string, wait: boolean, timeout: number): void;
  sendChunked(requestId: string, base64: string, chunkSize: number, delayMs: number): void;
};

declare global {
  interface Window {
    FicheroAndroid?: NativeBridge;
    __ficheroNative?: {
      resolve(id: string, value?: string): void;
      reject(id: string, error?: string): void;
      emitDisconnect(): void;
    };
  }
}

const pending = new Map<string, { resolve: (value: string) => void; reject: (error: Error) => void }>();
let seq = 0;
let activeDisconnectHandler: (() => void) | null = null;

function ensureNativeCallbacks() {
  if (window.__ficheroNative) return;
  window.__ficheroNative = {
    resolve(id: string, value = "") {
      const p = pending.get(id);
      if (!p) return;
      pending.delete(id);
      p.resolve(value);
    },
    reject(id: string, error = "Native operation failed") {
      const p = pending.get(id);
      if (!p) return;
      pending.delete(id);
      p.reject(new Error(error));
    },
    emitDisconnect() {
      activeDisconnectHandler?.();
    },
  };
}

function nativeCall(invoke: (bridge: NativeBridge, id: string) => void): Promise<string> {
  ensureNativeCallbacks();
  const bridge = window.FicheroAndroid;
  if (!bridge) {
    return Promise.reject(new Error("Android BLE bridge unavailable"));
  }
  const id = `r${Date.now()}_${++seq}`;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    try {
      invoke(bridge, id);
    } catch (e) {
      pending.delete(id);
      reject(e instanceof Error ? e : new Error(String(e)));
    }
  });
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const step = 0x8000;
  for (let i = 0; i < bytes.length; i += step) {
    binary += String.fromCharCode(...bytes.subarray(i, i + step));
  }
  return btoa(binary);
}

function base64ToBytes(value: string): Uint8Array {
  if (!value) return new Uint8Array();
  const binary = atob(value);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function makePacket(data: readonly number[] | number[] | Uint8Array): Packet {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data);
  const cmd = bytes.length >= 2 ? (bytes[0] << 8) | bytes[1] : bytes[0] ?? 0;
  return { command: cmd, toBytes: () => bytes };
}

export class FicheroClient extends TypedEventEmitter<ClientEventMap> {
  private heartbeatTimer: ReturnType<typeof setInterval> | undefined;
  private heartbeatFailCount = 0;
  private packetIntervalMs = 20;
  private info: PrinterInfo = {};
  private connected = false;

  readonly abstraction = {
    newPrintTask: (
      _name: PrintTaskName,
      opts: {
        totalPages: number;
        density: number;
        speed: number;
        labelType: LabelType;
        statusPollIntervalMs: number;
        statusTimeoutMs: number;
      },
    ) => new FicheroPrintTask(
      opts,
      (data, wait, timeout) => this.sendCommand(data, wait, timeout),
      (data) => this.sendChunked(data),
      (e) => this.emit("printprogress", e),
    ),

    printEnd: async () => {},
    printerReset: async () => { await this.sendCommand(CMD.factoryReset, true); },
    rfidInfo: async (): Promise<RfidInfo> => ({}),
    rfidInfo2: async (): Promise<RfidInfo> => ({}),
    setSoundEnabled: async (_type: number, _enabled: boolean) => {},
    firmwareUpgrade: async (_data: Uint8Array, _version: string) => {
      throw new Error("Firmware upgrade not implemented");
    },
  };

  async connect(_opts?: { deviceId?: string }): Promise<void> {
    if (!window.FicheroAndroid) throw new Error("This build requires Android");
    const name = await nativeCall((bridge, id) => bridge.connect(id));
    this.connected = true;
    activeDisconnectHandler = () => this.onDisconnected();
    this.emit("connect", { info: { deviceName: name || "FICHERO" } });
    await this.fetchPrinterInfo();
    this.startHeartbeat();
  }

  disconnect(): void {
    this.stopHeartbeat();
    try { window.FicheroAndroid?.disconnect(); } catch {}
    this.onDisconnected();
  }

  private onDisconnected(): void {
    if (!this.connected && !activeDisconnectHandler) return;
    this.connected = false;
    this.stopHeartbeat();
    this.info = {};
    if (activeDisconnectHandler) activeDisconnectHandler = null;
    this.emit("disconnect", undefined as unknown as void);
  }

  async sendCommand(
    data: readonly number[] | number[] | Uint8Array,
    wait = false,
    timeout = 2000,
  ): Promise<Uint8Array> {
    if (!this.connected) throw new Error("Not connected");
    const bytes = data instanceof Uint8Array ? data : new Uint8Array(data);
    this.emit("packetsent", { packet: makePacket(bytes) });
    const responseB64 = await nativeCall((bridge, id) =>
      bridge.send(id, bytesToBase64(bytes), wait, timeout),
    );
    const response = base64ToBytes(responseB64);
    if (wait && response.length) this.emit("packetreceived", { packet: makePacket(response) });
    if (this.packetIntervalMs > 0) await Utils.sleep(this.packetIntervalMs);
    return response;
  }

  async sendChunked(data: Uint8Array): Promise<void> {
    if (!this.connected) throw new Error("Not connected");
    await nativeCall((bridge, id) =>
      bridge.sendChunked(id, bytesToBase64(data), CHUNK_SIZE, CHUNK_DELAY_MS),
    );
  }

  private decodeResponse(buf: number[] | Uint8Array): string {
    return new TextDecoder().decode(new Uint8Array(buf)).trim();
  }

  async fetchPrinterInfo(): Promise<void> {
    const info: PrinterInfo = {};
    let r = await this.sendCommand(CMD.getModel, true);
    info.modelId = this.decodeResponse(r);
    r = await this.sendCommand(CMD.getFirmware, true);
    info.firmware = this.decodeResponse(r);
    r = await this.sendCommand(CMD.getSerial, true);
    info.serial = this.decodeResponse(r);
    r = await this.sendCommand(CMD.getBattery, true);
    if (r.length >= 2) {
      info.battery = r[r.length - 1];
      info.charging = r[r.length - 2] !== 0;
    }
    r = await this.sendCommand(CMD.getStatus, true);
    if (r.length > 0) info.status = this.parseStatusByte(r[r.length - 1]);
    this.info = info;
    this.emit("printerinfofetched", { info });
  }

  private parseStatusByte(byte: number): string {
    const flags: string[] = [];
    if (byte & 0x01) flags.push("printing");
    if (byte & 0x02) flags.push("cover open");
    if (byte & 0x04) flags.push("no paper");
    if (byte & 0x08) flags.push("low battery");
    if (byte & 0x10 || byte & 0x40) flags.push("overheated");
    if (byte & 0x20) flags.push("charging");
    return flags.length ? flags.join(", ") : "ready";
  }

  getModelMetadata(): PrinterModelMeta {
    return {
      model: this.info.modelId ?? "D11s",
      printheadPixels: 96,
      printDirection: "left" as const,
      densityMin: 0,
      densityMax: 2,
      densityDefault: 2,
      paperTypes: [1, 2, 3],
    };
  }

  getPrintTaskType(): PrintTaskName { return "B1"; }
  setPacketInterval(ms: number): void { this.packetIntervalMs = ms; }

  startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatFailCount = 0;
    this.heartbeatTimer = setInterval(async () => {
      try {
        const r = await this.sendCommand(CMD.getBattery, true);
        if (r.length >= 2) {
          this.heartbeatFailCount = 0;
          this.emit("heartbeat", {
            data: { chargeLevel: r[r.length - 1], charging: r[r.length - 2] !== 0 },
          });
        }
      } catch {
        this.heartbeatFailCount++;
        this.emit("heartbeatfailed", { failedAttempts: this.heartbeatFailCount });
      }
    }, 5000);
  }

  stopHeartbeat(): void {
    if (this.heartbeatTimer !== undefined) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = undefined;
    }
  }
}

export class FicheroBluetoothClient extends FicheroClient {}
export function instantiateClient(_type?: string): FicheroClient {
  ensureNativeCallbacks();
  return new FicheroBluetoothClient();
}

// Keep imports referenced so upstream type/build checks stay aligned with the web implementation.
void SERVICE_UUID;
void WRITE_CHAR_UUID;
void NOTIFY_CHAR_UUID;
