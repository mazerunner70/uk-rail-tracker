resource "aws_security_group" "ec2" {
  name        = "${local.name}-ec2"
  description = "urt-lt EC2: SSM admin; API :8080 only if open_api_to_internet (JWT is origin lock)"
  vpc_id      = data.aws_vpc.default.id

  # No SSH — prefer SSM Session Manager.

  dynamic "ingress" {
    for_each = var.open_api_to_internet ? [1] : []
    content {
      description = "HTTP API origin (API Gateway to EC2). Protect with Cognito JWT on Node."
      from_port   = 8080
      to_port     = 8080
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
    }
  }

  egress {
    description = "All egress (dnf, DNS, HTTPS, Kafka :9092)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name}-ec2"
  }
}
