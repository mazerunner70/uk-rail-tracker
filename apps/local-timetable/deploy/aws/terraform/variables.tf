variable "aws_region" {
  type        = string
  description = "Primary region (compute, S3, API Gateway, Cognito)."
  default     = "eu-west-2"
}

variable "project" {
  type        = string
  description = "Name prefix / Project tag."
  default     = "urt-lt"
}

variable "budget_limit_usd" {
  type        = number
  description = "Monthly COST budget amount (USD)."
  default     = 20
}

variable "budget_notification_email" {
  type        = string
  description = "Optional email for budget alerts (empty = budget only, no SNS subscriber)."
  default     = ""
}

variable "instance_type" {
  type        = string
  description = "EC2 instance type (arm64 Graviton)."
  default     = "t4g.micro"
}

variable "root_volume_gb" {
  type        = number
  description = "gp3 root volume size."
  default     = 8
}

variable "enable_front_door" {
  type        = bool
  description = "Create Cognito + HTTP API + CloudFront. Set false for compute-only first apply."
  default     = true
}

variable "open_api_to_internet" {
  type        = bool
  description = "SG ingress TCP 8080 from 0.0.0.0/0 so API Gateway HTTP integration can reach EC2. Origin lock is Cognito JWT (Gateway + Node)."
  default     = true
}

variable "cognito_callback_urls" {
  type        = list(string)
  description = "Hosted UI callback URLs. After first front-door apply, add https://<cloudfront_domain>/ and re-apply."
  default     = ["http://localhost:5173/"]
}

variable "cognito_logout_urls" {
  type        = list(string)
  description = "Hosted UI logout URLs."
  default     = ["http://localhost:5173/"]
}

variable "cognito_domain_prefix" {
  type        = string
  description = "Cognito Hosted UI domain prefix (must be globally unique)."
  default     = ""
}

variable "mca_reception_bucket" {
  type        = string
  description = "External RailData ingest bucket (timetable_full.zip)."
  default     = "raildata-ingest-0701"
}

variable "mca_reception_key" {
  type        = string
  description = "Object key for the full timetable zip (RailData uses a leading slash)."
  default     = "/timetable/timetable_full.zip"
}

variable "schematic_lambda_image_tag" {
  type        = string
  description = "ECR image tag for the schematic-process Lambda (pushed by schematic-process-aws.sh)."
  default     = "latest"
}

variable "enable_reception_s3_notification" {
  type        = bool
  description = "Attach ObjectCreated notification on the reception bucket → schematic Lambda. Replaces that bucket's notification config."
  default     = true
}
