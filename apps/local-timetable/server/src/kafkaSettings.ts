export type KafkaSettings = {
  bootstrapServers: string;
  consumerKey: string;
  consumerSecret: string;
  groupId: string;
  topicJson: string;
  sslCaLocation: string | null;
};

export type KafkaSettingsResult =
  | { ok: true; settings: KafkaSettings }
  | { ok: false; reason: "not-configured" };

function trim(value: string | undefined): string | null {
  const text = value?.trim();
  return text ? text : null;
}

export function readKafkaSettings(
  env: NodeJS.ProcessEnv = process.env,
): KafkaSettingsResult {
  const bootstrapServers = trim(env.KAFKA_BOOTSTRAP_SERVERS);
  const consumerKey = trim(env.KAFKA_CONSUMER_KEY);
  const consumerSecret = trim(env.KAFKA_CONSUMER_SECRET);
  const groupId = trim(env.KAFKA_GROUP_ID);
  const topicJson = trim(env.KAFKA_TOPIC_JSON);
  if (
    !bootstrapServers ||
    !consumerKey ||
    !consumerSecret ||
    !groupId ||
    !topicJson
  ) {
    return { ok: false, reason: "not-configured" };
  }
  return {
    ok: true,
    settings: {
      bootstrapServers,
      consumerKey,
      consumerSecret,
      groupId,
      topicJson,
      sslCaLocation: trim(env.KAFKA_SSL_CA_LOCATION),
    },
  };
}

export function kafkaJsConsumerConfig(settings: KafkaSettings): {
  clientId: string;
  brokers: string[];
  ssl: true | { ca: string[] };
  sasl: { mechanism: "plain"; username: string; password: string };
  groupId: string;
} {
  return {
    clientId: "local-timetable-kafka-overlay",
    brokers: settings.bootstrapServers.split(",").map((part) => part.trim()),
    ssl: settings.sslCaLocation
      ? { ca: [settings.sslCaLocation] }
      : true,
    sasl: {
      mechanism: "plain",
      username: settings.consumerKey,
      password: settings.consumerSecret,
    },
    groupId: settings.groupId,
  };
}
