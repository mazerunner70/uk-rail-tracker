locals {
  name = var.project

  account_id = data.aws_caller_identity.current.account_id

  bucket_fe     = "${local.name}-fe-${local.account_id}"
  bucket_mca    = "${local.name}-mca-${local.account_id}"
  bucket_deploy = "${local.name}-deploy-${local.account_id}"

  cognito_domain_prefix = var.cognito_domain_prefix != "" ? var.cognito_domain_prefix : "${local.name}-${local.account_id}"

  user_data = <<-EOF
    #!/bin/bash
    set -euxo pipefail
    dnf install -y docker git awscli
    systemctl enable --now docker
    mkdir -p /db /mca /opt/urt-lt/env /opt/urt-lt/app
    chmod 755 /db /mca /opt/urt-lt
    usermod -aG docker ec2-user 2>/dev/null || true
    usermod -aG docker ssm-user 2>/dev/null || true
    echo "urt-lt host bootstrap ok" > /opt/urt-lt/bootstrap.ok
  EOF
}
