# urt-lt AWS (Terraform)

IaC for [ADR-002](https://mazer92.atlassian.net/wiki/spaces/URT/pages/98371) / `docs/aws-deployment-plan.md`.

**Creates:** budget, default-VPC EC2 (`t4g.micro` AL2023 arm64 + Docker user-data), IAM (SSM + S3 read), SG, S3 (`fe` / `mca` / `deploy`), and optionally Cognito + HTTP API JWT + CloudFront (S3 + `/api*`).

**Does not create:** Node JWT middleware, FE Cognito wiring, on-box container (use `../run-server.sh`), Kafka SSM secrets (add later).

## Remote state (once per account)

```bash
# Creates tfstate-<account> + tfstate-lock-<account> if missing (idempotent)
../bootstrap-tf-backend.sh
```

State key for this stack: `urt/local-timetable/aws/terraform.tfstate` (other stacks: other keys in the same bucket).

## Apply

```bash
cd apps/local-timetable/deploy/aws/terraform
cp terraform.tfvars.example terraform.tfvars   # edit email / flags
terraform init
terraform plan
terraform apply
```

Compute-only first (no Cognito/CF spend beyond EC2/S3/budget):

```bash
terraform apply -var='enable_front_door=false'
```

## Notes

- API Gateway reaches EC2 over the **public IP** on `:8080`. SG opens 8080 when `open_api_to_internet=true`; **Gateway JWT + Node Cognito JWT** are the origin lock (no shared secret).
- Cognito SPA client uses `lifecycle.ignore_changes` on callback/logout URLs so CloudFront domain can be added in the console without a recreate fight. Or remove that lifecycle and set `cognito_*_urls` from `cloudfront_url`.
- Create the single user with `aws cognito-idp admin-create-user` after apply.
- Host ops scripts stay beside this folder: `setup-host.sh`, `run-server.sh`.
