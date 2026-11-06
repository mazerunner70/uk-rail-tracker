FROM node:22-bookworm-slim
WORKDIR /app
ENV TZ=Europe/London
COPY package.json tsconfig.json ./
COPY src ./src
RUN apt-get update \
  && apt-get install -y --no-install-recommends python3 make g++ \
  && npm install \
  && npx tsc \
  && npm prune --omit=dev \
  && apt-get purge -y python3 make g++ \
  && apt-get autoremove -y \
  && rm -rf /var/lib/apt/lists/*
CMD ["node", "dist/main.js"]
