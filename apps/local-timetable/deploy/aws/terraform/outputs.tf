output "account_id" {
  value = local.account_id
}

output "region" {
  value = data.aws_region.current.name
}

output "ami_id" {
  value = data.aws_ami.al2023_arm64.id
}

output "instance_id" {
  value = aws_instance.server.id
}

output "instance_public_ip" {
  value = aws_eip.server.public_ip
}

output "instance_eip_allocation_id" {
  value = aws_eip.server.id
}

output "security_group_id" {
  value = aws_security_group.ec2.id
}

output "s3_fe_bucket" {
  value = aws_s3_bucket.fe.bucket
}

output "s3_mca_bucket" {
  value = aws_s3_bucket.mca.bucket
}

output "s3_deploy_bucket" {
  value = aws_s3_bucket.deploy.bucket
}

output "budget_name" {
  value = aws_budgets_budget.monthly.name
}

output "cloudfront_domain" {
  value = var.enable_front_door ? aws_cloudfront_distribution.fe[0].domain_name : null
}

output "cloudfront_url" {
  value = var.enable_front_door ? "https://${aws_cloudfront_distribution.fe[0].domain_name}/" : null
}

output "api_gateway_endpoint" {
  value = var.enable_front_door ? aws_apigatewayv2_api.http[0].api_endpoint : null
}

output "cognito_user_pool_id" {
  value = var.enable_front_door ? aws_cognito_user_pool.main[0].id : null
}

output "cognito_client_id" {
  value = var.enable_front_door ? aws_cognito_user_pool_client.spa[0].id : null
}

output "cognito_hosted_ui_domain" {
  value = var.enable_front_door ? "${aws_cognito_user_pool_domain.main[0].domain}.auth.${data.aws_region.current.name}.amazoncognito.com" : null
}

output "cognito_issuer" {
  value = var.enable_front_door ? "https://cognito-idp.${data.aws_region.current.name}.amazonaws.com/${aws_cognito_user_pool.main[0].id}" : null
}

output "ecr_schematic_repository_url" {
  value = aws_ecr_repository.schematic.repository_url
}

output "schematic_lambda_name" {
  # Stable name (not taken from the resource) so ops scripts work before CreateFunction succeeds.
  value = "${local.name}-schematic-process"
}

output "next_steps" {
  value = <<-EOT
    1. SSM: aws ssm start-session --target ${aws_instance.server.id} --region ${data.aws_region.current.name}
    2. Confirm bootstrap: cat /opt/urt-lt/bootstrap.ok && docker --version
    3. Upload sources to s3://${aws_s3_bucket.deploy.bucket}/ and pull on box; run deploy/aws/run-server.sh
    4. Build/push schematic Lambda: pnpm schematic-process:aws (from apps/local-timetable)
    5. After CloudFront exists, add https://<cloudfront_domain>/ to Cognito callback/logout URLs
    6. Create one Cognito user (admin-create); wire FE Hosted UI + Bearer
  EOT
}
