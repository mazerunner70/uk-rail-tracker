import { Kafka, type Consumer } from "kafkajs";
import type BetterSqlite3 from "better-sqlite3";
import {
  kafkaJsConsumerConfig,
  type KafkaSettings,
} from "./kafkaSettings.js";
import {
  applyKafkaMessage,
  kafkaOffsetsForTopic,
  startOffsetForPartition,
} from "./overlayWriter.js";

export type PolledMessage = {
  topic: string;
  partition: number;
  offset: number;
  value: Buffer | string | null;
};

export function shouldSkipOffset(
  nextOffset: number | undefined,
  messageOffset: number,
): boolean {
  return nextOffset !== undefined && messageOffset < nextOffset;
}

export type LiveConsumer = {
  stop: () => Promise<void>;
};

/** Pure seek choice: saved next offset, or high watermark when unsaved. */
export function seekOffsetsFromSaved(
  consumer: {
    seek: (input: { topic: string; partition: number; offset: string }) => void;
  },
  topic: string,
  partitions: ReadonlyArray<{ partition: number; high: number }>,
  saved: Map<number, number>,
): void {
  for (const part of partitions) {
    const offset = startOffsetForPartition(saved.get(part.partition), part.high);
    consumer.seek({ topic, partition: part.partition, offset: String(offset) });
  }
}

export function applyPolledMessage(
  db: BetterSqlite3.Database,
  msg: PolledMessage,
): void {
  const saved = kafkaOffsetsForTopic(db, msg.topic);
  if (shouldSkipOffset(saved.get(msg.partition), msg.offset)) {
    return;
  }
  if (msg.value === null) {
    return;
  }
  applyKafkaMessage(db, msg.value, {
    topic: msg.topic,
    partition: msg.partition,
    offset: msg.offset,
  });
}

/** Drain an injected poll stream until it ends (tests). */
export async function drainPolledMessages(
  db: BetterSqlite3.Database,
  messages: AsyncIterable<PolledMessage>,
  isPaused: () => boolean = () => false,
): Promise<void> {
  for await (const msg of messages) {
    if (isPaused()) {
      break;
    }
    try {
      applyPolledMessage(db, msg);
    } catch (err) {
      const text = err instanceof Error ? err.message : String(err);
      console.error("kafka overlay apply failed", text);
      throw err;
    }
  }
}

export async function startLiveConsumer(options: {
  db: BetterSqlite3.Database;
  settings: KafkaSettings;
  isPaused: () => boolean;
}): Promise<LiveConsumer> {
  const { db, settings, isPaused } = options;
  const topic = settings.topicJson;
  const config = kafkaJsConsumerConfig(settings);
  const kafka = new Kafka({
    clientId: config.clientId,
    brokers: config.brokers,
    ssl: config.ssl,
    sasl: config.sasl,
  });
  const consumer: Consumer = kafka.consumer({
    groupId: config.groupId,
    allowAutoTopicCreation: false,
  });
  await consumer.connect();
  await consumer.subscribe({ topic, fromBeginning: false });

  const { GROUP_JOIN } = consumer.events;
  consumer.on(GROUP_JOIN, async () => {
    const saved = kafkaOffsetsForTopic(db, topic);
    const admin = kafka.admin();
    await admin.connect();
    try {
      const described = await admin.fetchTopicOffsets(topic);
      seekOffsetsFromSaved(
        consumer,
        topic,
        described.map((part) => ({
          partition: part.partition,
          high: Number(part.high),
        })),
        saved,
      );
    } finally {
      await admin.disconnect();
    }
  });

  const running = consumer.run({
    autoCommit: false,
    eachMessage: async ({ topic: msgTopic, partition, message }) => {
      if (isPaused()) {
        return;
      }
      try {
        applyPolledMessage(db, {
          topic: msgTopic,
          partition,
          offset: Number(message.offset),
          value: message.value ? Buffer.from(message.value) : null,
        });
      } catch (err) {
        const text = err instanceof Error ? err.message : String(err);
        console.error("kafka overlay apply failed", text);
        throw err;
      }
    },
  });

  return {
    stop: async () => {
      await consumer.stop().catch(() => undefined);
      await consumer.disconnect().catch(() => undefined);
      await running.catch(() => undefined);
    },
  };
}
