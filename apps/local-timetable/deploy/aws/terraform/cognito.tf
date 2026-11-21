resource "aws_cognito_user_pool" "main" {
  count = var.enable_front_door ? 1 : 0

  name = "${local.name}-users"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  password_policy {
    minimum_length    = 12
    require_lowercase = true
    require_numbers   = true
    require_symbols   = true
    require_uppercase = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

resource "aws_cognito_user_pool_domain" "main" {
  count = var.enable_front_door ? 1 : 0

  domain       = local.cognito_domain_prefix
  user_pool_id = aws_cognito_user_pool.main[0].id
}

resource "aws_cognito_user_pool_client" "spa" {
  count = var.enable_front_door ? 1 : 0

  name         = "${local.name}-spa"
  user_pool_id = aws_cognito_user_pool.main[0].id

  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]

  callback_urls = var.cognito_callback_urls
  logout_urls   = var.cognito_logout_urls

  explicit_auth_flows = [
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_SRP_AUTH",
  ]

  prevent_user_existence_errors = "ENABLED"

  # Avoid recreate churn while iterating callback URLs after CloudFront exists.
  lifecycle {
    ignore_changes = [callback_urls, logout_urls]
  }
}
