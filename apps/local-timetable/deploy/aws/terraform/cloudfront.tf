resource "aws_cloudfront_origin_access_control" "fe" {
  count = var.enable_front_door ? 1 : 0

  name                              = "${local.name}-fe-oac"
  description                       = "OAC for ${local.bucket_fe}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "fe" {
  count = var.enable_front_door ? 1 : 0

  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${local.name} screen + /api"
  default_root_object = "index.html"
  price_class         = "PriceClass_100"

  origin {
    domain_name              = aws_s3_bucket.fe.bucket_regional_domain_name
    origin_id                = "s3-fe"
    origin_access_control_id = aws_cloudfront_origin_access_control.fe[0].id
  }

  origin {
    domain_name = replace(aws_apigatewayv2_api.http[0].api_endpoint, "https://", "")
    origin_id   = "apigw-http"

    custom_origin_config {
      http_port              = 443
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-fe"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 3600
    max_ttl     = 86400
  }

  ordered_cache_behavior {
    path_pattern           = "/api*"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "apigw-http"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    forwarded_values {
      query_string = true
      headers      = ["Authorization", "Origin", "Content-Type"]
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

data "aws_iam_policy_document" "fe_oac" {
  count = var.enable_front_door ? 1 : 0

  statement {
    sid     = "AllowCloudFrontOAC"
    actions = ["s3:GetObject"]
    resources = [
      "${aws_s3_bucket.fe.arn}/*",
    ]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.fe[0].arn]
    }
  }
}

resource "aws_s3_bucket_policy" "fe" {
  count = var.enable_front_door ? 1 : 0

  bucket = aws_s3_bucket.fe.id
  policy = data.aws_iam_policy_document.fe_oac[0].json
}
