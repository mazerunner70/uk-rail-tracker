resource "aws_instance" "server" {
  ami                         = data.aws_ami.al2023_arm64.id
  instance_type               = var.instance_type
  subnet_id                   = sort(data.aws_subnets.default_public.ids)[0]
  vpc_security_group_ids      = [aws_security_group.ec2.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2.name
  associate_public_ip_address = true
  user_data                   = local.user_data
  user_data_replace_on_change = true

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    delete_on_termination = true
    encrypted             = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = {
    Name = "${local.name}-ec2"
  }

  lifecycle {
    ignore_changes = [ami]
  }
}

# Stable public IPv4 for API Gateway HTTP origin (survives stop/start).
resource "aws_eip" "server" {
  domain   = "vpc"
  instance = aws_instance.server.id

  tags = {
    Name = "${local.name}-ec2"
  }

  depends_on = [aws_instance.server]
}
