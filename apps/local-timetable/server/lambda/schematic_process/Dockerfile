# arm64 Python Lambda for MCA extract + octilinear schematic.
# Prefer: pnpm schematic-process:aws (buildx --platform linux/arm64 --push).
# Manual from apps/local-timetable/server:
#   docker buildx build -f lambda/schematic_process/Dockerfile --platform linux/arm64 --load -t urt-lt-schematic-process .
FROM public.ecr.aws/lambda/python:3.12-arm64

WORKDIR ${LAMBDA_TASK_ROOT}

COPY layout/requirements.txt /tmp/layout-requirements.txt
COPY lambda/schematic_process/requirements.txt /tmp/lambda-requirements.txt
RUN pip install --no-cache-dir -r /tmp/layout-requirements.txt -r /tmp/lambda-requirements.txt

COPY layout/metro_layout.py ./layout/metro_layout.py
COPY data/station-geo.json ./data/station-geo.json
COPY lambda/schematic_process/ ./schematic_process/

ENV METRO_LAYOUT_TIME_LIMIT_SEC=840
ENV PYTHONPATH=${LAMBDA_TASK_ROOT}

CMD ["schematic_process.handler.handler"]
