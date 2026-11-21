resource "aws_apigatewayv2_api" "http" {
  count = var.enable_front_door ? 1 : 0

  name          = "${local.name}-http"
  protocol_type = "HTTP"

  cors_configuration {
    allow_headers = ["authorization", "content-type"]
    allow_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
    allow_origins = ["*"]
    max_age       = 300
  }
}

resource "aws_apigatewayv2_authorizer" "cognito_jwt" {
  count = var.enable_front_door ? 1 : 0

  api_id           = aws_apigatewayv2_api.http[0].id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "${local.name}-cognito-jwt"

  jwt_configuration {
    audience = [aws_cognito_user_pool_client.spa[0].id]
    issuer   = "https://cognito-idp.${data.aws_region.current.name}.amazonaws.com/${aws_cognito_user_pool.main[0].id}"
  }
}

resource "aws_apigatewayv2_integration" "ec2" {
  count = var.enable_front_door ? 1 : 0

  api_id             = aws_apigatewayv2_api.http[0].id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  # Strip /api prefix: client calls /api/network → origin /network
  integration_uri        = "http://${aws_eip.server.public_ip}:8080/{proxy}"
  payload_format_version = "1.0"

  request_parameters = {
    "overwrite:path" = "/$request.path.proxy"
  }
}

resource "aws_apigatewayv2_route" "api_proxy" {
  count = var.enable_front_door ? 1 : 0

  api_id             = aws_apigatewayv2_api.http[0].id
  route_key          = "ANY /api/{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.ec2[0].id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_jwt[0].id
}

resource "aws_apigatewayv2_stage" "default" {
  count = var.enable_front_door ? 1 : 0

  api_id      = aws_apigatewayv2_api.http[0].id
  name        = "$default"
  auto_deploy = true
}
