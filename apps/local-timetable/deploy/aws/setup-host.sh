#!/usr/bin/env bash
# Run once on the AL2023 arm64 instance (SSM shell). Idempotent.
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "run as root (sudo -i)" >&2
  exit 1
fi

dnf install -y docker git
systemctl enable --now docker

mkdir -p /db /mca /opt/urt-lt /opt/urt-lt/env
chmod 755 /db /mca /opt/urt-lt

# ec2-user may not exist on all images; ignore failure
usermod -aG docker ec2-user 2>/dev/null || true
usermod -aG docker ssm-user 2>/dev/null || true

docker --version
echo "host ready: /db /mca /opt/urt-lt"
