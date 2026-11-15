FROM node:22-bookworm-slim
WORKDIR /app
ENV TZ=Europe/London
COPY package.json tsconfig.json ./
COPY src ./src
COPY data ./data
COPY layout ./layout
RUN apt-get update \
  && apt-get install -y --no-install-recommends python3 python3-pip python3-venv make g++ \
  && npm install \
  && npx tsc \
  && npm prune --omit=dev \
  && python3 -m venv /opt/metro-layout \
  && /opt/metro-layout/bin/pip install --no-cache-dir -r layout/requirements.txt \
  && apt-get purge -y make g++ \
  && apt-get autoremove -y \
  && rm -rf /var/lib/apt/lists/*
ENV METRO_LAYOUT_PYTHON=/opt/metro-layout/bin/python
ENV METRO_LAYOUT_ENGINE=milp
ENV METRO_LAYOUT_TIME_LIMIT_SEC=1200
CMD ["node", "dist/main.js"]
