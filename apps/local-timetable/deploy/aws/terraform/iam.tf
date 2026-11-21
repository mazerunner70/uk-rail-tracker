data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "${local.name}-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "ec2_app" {
  statement {
    sid = "SsmParams"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = [
      "arn:aws:ssm:${data.aws_region.current.name}:${local.account_id}:parameter/${local.name}/*",
    ]
  }

  statement {
    sid       = "SsmDecrypt"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${data.aws_region.current.name}.amazonaws.com"]
    }
  }

  statement {
    sid = "S3McaAndDeploy"
    actions = [
      "s3:GetObject",
      "s3:ListBucket",
    ]
    resources = [
      aws_s3_bucket.mca.arn,
      "${aws_s3_bucket.mca.arn}/*",
      aws_s3_bucket.deploy.arn,
      "${aws_s3_bucket.deploy.arn}/*",
    ]
  }

  # RailData reception: head/get timetable_full.zip for EBS staging.
  statement {
    sid = "S3McaReception"
    actions = [
      "s3:GetObject",
      "s3:GetObjectVersion",
      "s3:ListBucket",
    ]
    resources = [
      "arn:aws:s3:::${var.mca_reception_bucket}",
      "arn:aws:s3:::${var.mca_reception_bucket}${var.mca_reception_key}",
      "arn:aws:s3:::${var.mca_reception_bucket}/timetable/*",
      "arn:aws:s3:::${var.mca_reception_bucket}/*",
    ]
  }
}

resource "aws_iam_role_policy" "ec2_app" {
  name   = "${local.name}-ec2-app"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ec2_app.json
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${local.name}-ec2-profile"
  role = aws_iam_role.ec2.name
}
