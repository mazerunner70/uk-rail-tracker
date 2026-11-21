terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Bootstrap once: ../bootstrap-tf-backend.sh
  # Shared account bucket + lock table; project prefix lives in the key.
  backend "s3" {
    bucket         = "tfstate-661792079381"
    key            = "urt/local-timetable/aws/terraform.tfstate"
    region         = "eu-west-2"
    dynamodb_table = "tfstate-lock-661792079381"
    encrypt        = true
  }
}
