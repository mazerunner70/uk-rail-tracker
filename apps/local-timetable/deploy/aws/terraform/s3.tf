resource "aws_s3_bucket" "fe" {
  bucket = local.bucket_fe
}

resource "aws_s3_bucket" "mca" {
  bucket = local.bucket_mca
}

resource "aws_s3_bucket" "deploy" {
  bucket = local.bucket_deploy
}

resource "aws_s3_bucket_public_access_block" "fe" {
  bucket                  = aws_s3_bucket.fe.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "mca" {
  bucket                  = aws_s3_bucket.mca.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "deploy" {
  bucket                  = aws_s3_bucket.deploy.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "fe" {
  bucket = aws_s3_bucket.fe.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_ownership_controls" "mca" {
  bucket = aws_s3_bucket.mca.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_ownership_controls" "deploy" {
  bucket = aws_s3_bucket.deploy.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}
