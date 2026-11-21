# Python Lambda: reception zip → stamped MCA + network-schematic.json artifacts.

resource "aws_ecr_repository" "schematic" {
  name                 = "${local.name}-schematic-process"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

resource "aws_ecr_lifecycle_policy" "schematic" {
  repository = aws_ecr_repository.schematic.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

data "aws_iam_policy_document" "schematic_lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "schematic_lambda" {
  name               = "${local.name}-schematic-lambda"
  assume_role_policy = data.aws_iam_policy_document.schematic_lambda_assume.json
}

data "aws_iam_policy_document" "schematic_lambda" {
  statement {
    sid = "Logs"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["arn:aws:logs:${data.aws_region.current.name}:${local.account_id}:*"]
  }

  statement {
    sid = "ReadReceptionZip"
    actions = [
      "s3:GetObject",
      "s3:GetObjectVersion",
      "s3:ListBucket",
    ]
    resources = [
      "arn:aws:s3:::${var.mca_reception_bucket}",
      "arn:aws:s3:::${var.mca_reception_bucket}/*",
      aws_s3_bucket.mca.arn,
      "${aws_s3_bucket.mca.arn}/*",
    ]
  }

  statement {
    sid = "WriteArtifacts"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:ListBucket",
    ]
    resources = [
      aws_s3_bucket.mca.arn,
      "${aws_s3_bucket.mca.arn}/artifacts/*",
    ]
  }

  statement {
    sid = "SsmApply"
    actions = [
      "ssm:SendCommand",
      "ssm:GetCommandInvocation",
    ]
    resources = [
      "arn:aws:ssm:${data.aws_region.current.name}::document/AWS-RunShellScript",
      aws_instance.server.arn,
      "arn:aws:ssm:${data.aws_region.current.name}:${local.account_id}:*",
    ]
  }
}

resource "aws_iam_role_policy" "schematic_lambda" {
  name   = "${local.name}-schematic-lambda"
  role   = aws_iam_role.schematic_lambda.id
  policy = data.aws_iam_policy_document.schematic_lambda.json
}

resource "aws_cloudwatch_log_group" "schematic_lambda" {
  name              = "/aws/lambda/${local.name}-schematic-process"
  retention_in_days = 14
}

resource "aws_lambda_function" "schematic" {
  function_name = "${local.name}-schematic-process"
  role          = aws_iam_role.schematic_lambda.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.schematic.repository_url}:${var.schematic_lambda_image_tag}"
  architectures = ["arm64"]
  timeout       = 900
  memory_size   = 3008

  ephemeral_storage {
    size = 10240
  }

  environment {
    variables = {
      ARTIFACT_BUCKET             = aws_s3_bucket.mca.bucket
      EC2_INSTANCE_ID             = aws_instance.server.id
      METRO_LAYOUT_TIME_LIMIT_SEC = "840"
    }
  }

  depends_on = [aws_cloudwatch_log_group.schematic_lambda]

  lifecycle {
    # Image is pushed by scripts/schematic-process-aws.sh; first apply may use a placeholder tag.
    ignore_changes = [image_uri]
  }
}

resource "aws_lambda_permission" "schematic_s3" {
  statement_id  = "AllowReceptionBucketInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.schematic.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = "arn:aws:s3:::${var.mca_reception_bucket}"
}

# WARNING: replaces the notification configuration on the reception bucket.
resource "aws_s3_bucket_notification" "reception_schematic" {
  count  = var.enable_reception_s3_notification ? 1 : 0
  bucket = var.mca_reception_bucket

  lambda_function {
    lambda_function_arn = aws_lambda_function.schematic.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "/timetable/"
    filter_suffix       = ".zip"
  }

  depends_on = [aws_lambda_permission.schematic_s3]
}
